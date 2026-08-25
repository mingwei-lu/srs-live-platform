package com.srs.live.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.srs.live.common.exception.BusinessException;
import com.srs.live.common.util.SnowflakeIdGenerator;
import com.srs.live.dto.request.LoginRequest;
import com.srs.live.dto.request.RegisterRequest;
import com.srs.live.dto.response.LoginResponse;
import com.srs.live.dto.response.PageResult;
import com.srs.live.entity.User;
import com.srs.live.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final SnowflakeIdGenerator idGenerator;

    public UserService(UserMapper userMapper, JwtService jwtService,
                       SnowflakeIdGenerator idGenerator) {
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.idGenerator = idGenerator;
    }

    public LoginResponse register(RegisterRequest request) {
        // 检查手机号是否已注册
        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, request.getPhone()));
        if (existing != null) {
            throw new BusinessException(40011, "phone already registered");
        }

        // 检查相同辨识组合是否已存在
        if (request.getUsername() != null && !request.getUsername().isEmpty()) {
            User sameIdentity = findByIdentity(request.getUsername(), request.getCompany(),
                    request.getDepartment(), request.getPhoneTail());
            if (sameIdentity != null) {
                throw new BusinessException(40010, "username with same identity already exists");
            }
        }

        User user = new User();
        user.setId(idGenerator.nextId());
        user.setUid(String.valueOf(idGenerator.nextId()));
        user.setPhone(request.getPhone());
        user.setUsername(request.getUsername() != null ? request.getUsername() : "");
        user.setCompany(request.getCompany() != null ? request.getCompany() : "");
        user.setDepartment(request.getDepartment() != null ? request.getDepartment() : "");
        user.setPhoneTail(request.getPhoneTail() != null ? request.getPhoneTail() : "");
        user.setPasswordHash(hashPassword(request.getPassword()));
        user.setRole("user");
        userMapper.insert(user);

        String token = jwtService.generateToken(user.getUid(), user.getDisplayName(), user.getRole());
        return new LoginResponse(user.getUid(), user.getDisplayName(), user.getRole(), token);
    }

    public LoginResponse login(LoginRequest request) {
        // 支持手机号或用户名登录
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getPhone, request.getPhone())
                        .eq(User::getStatus, 1));
        if (user == null) {
            // 尝试用用户名登录
            user = userMapper.selectOne(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getUsername, request.getPhone())
                            .eq(User::getStatus, 1));
        }
        if (user == null) {
            throw new BusinessException(40000, "invalid phone or password");
        }
        if (!new BCryptPasswordEncoder().matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(40000, "invalid phone or password");
        }

        String token = jwtService.generateToken(user.getUid(), user.getDisplayName(), user.getRole());
        return new LoginResponse(user.getUid(), user.getDisplayName(), user.getRole(), token);
    }

    /** 检查手机号是否已注册 */
    public boolean isPhoneRegistered(String phone) {
        return userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getPhone, phone)) > 0;
    }

    public User getByUid(String uid) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUid, uid));
        if (user == null) {
            throw new BusinessException(40004, "user not found");
        }
        return user;
    }

    public List<User> listUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<User>().orderByDesc(User::getCreatedAt));
    }

    // ========== 管理接口 ==========

    public PageResult<User> pageUsers(int page, int size, String keyword) {
        Page<User> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            qw.like(User::getUsername, keyword)
              .or()
              .like(User::getPhone, keyword);
        }
        qw.orderByDesc(User::getCreatedAt);
        Page<User> result = userMapper.selectPage(pageParam, qw);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    public User createUser(User user) {
        if (user.getPhone() == null || user.getPhone().isEmpty()) {
            throw new BusinessException(40001, "phone is required");
        }
        if (isPhoneRegistered(user.getPhone())) {
            throw new BusinessException(40011, "phone already registered");
        }
        user.setId(idGenerator.nextId());
        user.setUid(String.valueOf(idGenerator.nextId()));
        user.setPasswordHash(hashPassword(user.getPasswordHash() != null ? user.getPasswordHash() : "123456"));
        if (user.getRole() == null || user.getRole().isEmpty()) {
            user.setRole("user");
        }
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        userMapper.insert(user);
        return user;
    }

    public void updateUser(String uid, Map<String, Object> data) {
        User user = getByUid(uid);
        if (data.containsKey("username")) {
            user.setUsername((String) data.get("username"));
        }
        if (data.containsKey("phone")) {
            user.setPhone((String) data.get("phone"));
        }
        if (data.containsKey("role")) {
            user.setRole((String) data.get("role"));
        }
        if (data.containsKey("status")) {
            Object status = data.get("status");
            if (status instanceof Integer) {
                user.setStatus((Integer) status);
            } else if (status instanceof Number) {
                user.setStatus(((Number) status).intValue());
            }
        }
        if (data.containsKey("company")) {
            user.setCompany((String) data.get("company"));
        }
        if (data.containsKey("department")) {
            user.setDepartment((String) data.get("department"));
        }
        userMapper.updateById(user);
    }

    public void deleteUser(String uid) {
        User user = getByUid(uid);
        userMapper.deleteById(user.getId());
    }

    private User findByIdentity(String username, String company, String department, String phoneTail) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .eq(User::getCompany, company != null ? company : "")
                .eq(User::getDepartment, department != null ? department : "")
                .eq(User::getPhoneTail, phoneTail != null ? phoneTail : ""));
    }

    private String hashPassword(String password) {
        return new BCryptPasswordEncoder().encode(password);
    }
}
