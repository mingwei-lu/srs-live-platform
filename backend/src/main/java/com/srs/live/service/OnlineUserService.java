package com.srs.live.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.srs.live.common.constant.RedisKeys;
import com.srs.live.entity.OnlineUser;
import com.srs.live.mapper.OnlineUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OnlineUserService {

    private final OnlineUserMapper onlineUserMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public OnlineUserService(OnlineUserMapper onlineUserMapper,
                             RedisTemplate<String, Object> redisTemplate) {
        this.onlineUserMapper = onlineUserMapper;
        this.redisTemplate = redisTemplate;
    }

    public void userOnline(String uid, String username, String role, String roomId,
                           String wsSessionId, String nodeId) {
        // 写入 MySQL
        OnlineUser ou = new OnlineUser();
        ou.setUid(uid);
        ou.setUsername(username);
        ou.setRole(role);
        ou.setRoomId(roomId);
        ou.setWsSessionId(wsSessionId);
        ou.setNodeId(nodeId);
        ou.setLastHeartbeat(LocalDateTime.now());
        ou.setConnectedAt(LocalDateTime.now());
        onlineUserMapper.insert(ou);

        // 写入 Redis
        redisTemplate.opsForSet().add(RedisKeys.ONLINE_USERS, uid);
        redisTemplate.opsForSet().add(RedisKeys.roomUsers(roomId), uid);
        redisTemplate.opsForSet().add(RedisKeys.wsNodeUsers(nodeId), uid);
        updateHeartbeat(uid);
        updateUserNode(uid, nodeId);
    }

    public void userOffline(String uid) {
        LambdaQueryWrapper<OnlineUser> queryWrapper = new LambdaQueryWrapper<>();
        OnlineUser ou = onlineUserMapper.selectOne(queryWrapper.eq(OnlineUser::getUid, uid));
        if (ou == null) return;

        // 清理 Redis
        redisTemplate.opsForSet().remove(RedisKeys.ONLINE_USERS, uid);
        redisTemplate.opsForSet().remove(RedisKeys.roomUsers(ou.getRoomId()), uid);
        redisTemplate.opsForSet().remove(RedisKeys.wsNodeUsers(ou.getNodeId()), uid);
        redisTemplate.delete(RedisKeys.userHeartbeat(uid));
        redisTemplate.delete(RedisKeys.userNode(uid));
        LambdaQueryWrapper<OnlineUser> deleteWrapper = new LambdaQueryWrapper<>();
        // 清理 MySQL
        onlineUserMapper.delete(deleteWrapper.eq(OnlineUser::getUid, uid));
    }

    public void updateHeartbeat(String uid) {
        String key = RedisKeys.userHeartbeat(uid);
        redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()), 60, TimeUnit.SECONDS);
    }

    public void updateUserNode(String uid, String nodeId) {
        String key = RedisKeys.userNode(uid);
        redisTemplate.opsForValue().set(key, nodeId, 60, TimeUnit.SECONDS);
    }

    public void bindSrsClient(String uid, String srsClientId, String srsNodeId) {
        LambdaQueryWrapper<OnlineUser> query = new LambdaQueryWrapper<>();
        OnlineUser ou = onlineUserMapper.selectOne(query.eq(OnlineUser::getUid, uid));
        if (ou != null) {
            ou.setSrsClientId(srsClientId);
            ou.setSrsNodeId(srsNodeId);
            onlineUserMapper.updateById(ou);
        }
    }

    public List<Map<String, Object>> getRoomOnlineUsers(String roomId) {
        Set<Object> uids = redisTemplate.opsForSet().members(RedisKeys.roomUsers(roomId));
        if (uids == null || uids.isEmpty()) return Collections.emptyList();
        LambdaQueryWrapper<OnlineUser> query = new LambdaQueryWrapper<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object uidObj : uids) {
            String uid = uidObj.toString();
            OnlineUser ou = onlineUserMapper.selectOne(
                    query.eq(OnlineUser::getUid, uid));
            if (ou != null) {
                Map<String, Object> user = new HashMap<>();
                user.put("uid", ou.getUid());
                user.put("username", ou.getUsername());
                user.put("role", ou.getRole());
                user.put("connectedAt", ou.getConnectedAt());
                result.add(user);
            }
        }
        return result;
    }

    public long getOnlineCount() {
        Long count = redisTemplate.opsForSet().size(RedisKeys.ONLINE_USERS);
        return count != null ? count : 0;
    }

    public boolean isOnline(String uid) {
        Boolean isMember = redisTemplate.opsForSet().isMember(RedisKeys.ONLINE_USERS, uid);
        return Boolean.TRUE.equals(isMember);
    }
}