package com.srs.live.service;

import com.srs.live.common.enums.RoomStatus;
import com.srs.live.common.exception.BusinessException;
import com.srs.live.dto.request.CreateRoomRequest;
import com.srs.live.dto.request.UpdateRoomRequest;
import com.srs.live.dto.response.RoomResponse;
import com.srs.live.dto.response.StartLiveResponse;
import com.srs.live.entity.LiveRecord;
import com.srs.live.entity.Room;
import com.srs.live.entity.User;
import com.srs.live.mapper.RoomMapper;
import com.srs.live.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RoomService {

    private final RoomMapper roomMapper;
    private final UserMapper userMapper;
    private final ClusterService clusterService;
    private final LiveRecordService liveRecordService;

    public RoomService(RoomMapper roomMapper, UserMapper userMapper,
                       ClusterService clusterService, LiveRecordService liveRecordService) {
        this.roomMapper = roomMapper;
        this.userMapper = userMapper;
        this.clusterService = clusterService;
        this.liveRecordService = liveRecordService;
    }

    public RoomResponse createRoom(CreateRoomRequest request, String publisherUid) {
        Room room = new Room();
        room.setRoomId("room_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        room.setTitle(request.getTitle());
        room.setPublisherUid(publisherUid);
        room.setStatus(RoomStatus.WAITING.getCode());
        roomMapper.insert(room);
        return toResponse(room);
    }

    public RoomResponse getRoom(String roomId) {
        Room room = getRoomEntity(roomId);
        return toResponse(room);
    }

    public List<RoomResponse> listRooms(String status) {
        var qw = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Room>();
        if (status != null && !status.isEmpty()) {
            qw.eq(Room::getStatus, status);
        }
        qw.orderByDesc(Room::getCreatedAt);
        return roomMapper.selectList(qw).stream()
                .map(this::toResponse)
                .toList();
    }

    public RoomResponse updateRoom(String roomId, UpdateRoomRequest request, String operatorUid) {
        Room room = getRoomEntity(roomId);
        checkOwnerOrAdmin(room, operatorUid);

        if (request.getTitle() != null) {
            room.setTitle(request.getTitle());
        }
        roomMapper.updateById(room);
        return toResponse(room);
    }

    public void closeRoom(String roomId, String operatorUid) {
        Room room = getRoomEntity(roomId);
        checkOwnerOrAdmin(room, operatorUid);

        room.setStatus(RoomStatus.CLOSED.getCode());
        room.setClosedAt(LocalDateTime.now());
        roomMapper.updateById(room);
    }

    public StartLiveResponse startLive(String roomId, String uid, boolean recordingEnabled) {
        Room room = getRoomEntity(roomId);

        // 仅允许 waiting（首次开播）或 live（重新推流）状态
        if (!RoomStatus.WAITING.getCode().equals(room.getStatus())
                && !RoomStatus.LIVE.getCode().equals(room.getStatus())) {
            throw new BusinessException(40000, "room is not in waiting or live status");
        }

        boolean isRePublish = RoomStatus.LIVE.getCode().equals(room.getStatus());

        // 重新推流时校验主播权限（仅主播本人或管理员可重推）
        if (isRePublish) {
            checkOwnerOrAdmin(room, uid);
        }

        // 首次开播根据录制开关选节点类型；重新推流保持原有节点类型
        String nodeType;
        if (isRePublish) {
            nodeType = room.getNodeType() != null ? room.getNodeType() : "live";
        } else {
            nodeType = recordingEnabled ? "record" : "live";
        }

        String srsNode = clusterService.selectOptimalNode(roomId, nodeType);
        if (srsNode == null) {
            throw new BusinessException(50001, "no available SRS node");
        }

        String streamId = room.getRoomId();
        String whipUrl = clusterService.generateWhipUrl(srsNode, streamId, uid, nodeType);

        if (isRePublish) {
            // 重新推流：仅更新 SRS 节点信息，不改变房间状态和直播记录
            room.setSrsNode(srsNode);
            room.setNodeType(nodeType);
            roomMapper.updateById(room);
        } else {
            // 首次开播：完整初始化房间状态
            room.setStatus(RoomStatus.LIVE.getCode());
            room.setStartedAt(LocalDateTime.now());
            room.setPublisherUid(uid);
            room.setSrsNode(srsNode);
            room.setNodeType(nodeType);
            roomMapper.updateById(room);

            // 创建直播记录（含录播标记）
            liveRecordService.createRecord(roomId, uid, recordingEnabled);
        }

        return new StartLiveResponse(whipUrl, streamId, srsNode);
    }

    public void stopLive(String roomId, String operatorUid) {
        Room room = getRoomEntity(roomId);
        checkOwnerOrAdmin(room, operatorUid);

        room.setStatus(RoomStatus.CLOSED.getCode());
        room.setClosedAt(LocalDateTime.now());
        room.setSrsNode(null);
        roomMapper.updateById(room);

        // 结束直播记录（触发录播上传）
        LiveRecord record = liveRecordService.getActiveRecord(roomId);
        if (record != null) {
            liveRecordService.finishRecord(record.getId());
        }
    }

    public Room getRoomEntity(String roomId) {
        Room room = roomMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Room>()
                        .eq(Room::getRoomId, roomId));
        if (room == null) {
            throw new BusinessException(40004, "room not found");
        }
        return room;
    }

    /** 更新房间主播（SRS回调时调用，谁推流谁就是主播） */
    public void updatePublisher(Room room) {
        roomMapper.updateById(room);
    }

    /** 检查当前用户是否是房间主播或管理员 */
    public void checkOwnerOrAdmin(Room room, String operatorUid) {
        if (room.getPublisherUid().equals(operatorUid)) {
            return;
        }
        if (isAdmin(operatorUid)) {
            return;
        }
        throw new BusinessException(40003, "permission denied");
    }

    /** 判断用户是否是管理员（查数据库） */
    private boolean isAdmin(String uid) {
        User user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUid, uid));
        return user != null && "admin".equals(user.getRole());
    }

    /** 获取观众播放地址（WHEP） */
    public String getPlayUrl(String roomId, String uid) {
        Room room = getRoomEntity(roomId);
        if (!RoomStatus.LIVE.getCode().equals(room.getStatus())) {
            throw new BusinessException(40000, "room is not live");
        }
        if (room.getSrsNode() == null) {
            throw new BusinessException(50002, "no available SRS node");
        }
        // 根据房间记录的节点类型路由到对应的 srs-proxy
        String nodeType = room.getNodeType() != null ? room.getNodeType() : "live";
        return clusterService.generateWhepUrl(room.getSrsNode(), room.getRoomId(), uid, nodeType);
    }

    private RoomResponse toResponse(Room room) {
        // 查询主播显示名
        String displayName = room.getPublisherUid();
        User publisher = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUid, room.getPublisherUid()));
        if (publisher != null) {
            displayName = publisher.getDisplayName();
        }
        return new RoomResponse(
                room.getRoomId(), room.getTitle(), room.getPublisherUid(), displayName,
                room.getStatus(), room.getCreatedAt(), room.getStartedAt(), room.getClosedAt()
        );
    }
}