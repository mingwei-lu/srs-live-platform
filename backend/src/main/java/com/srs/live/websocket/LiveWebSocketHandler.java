package com.srs.live.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srs.live.common.constant.RedisKeys;
import com.srs.live.common.constant.WsMsgType;
import com.srs.live.common.util.JwtUtil;
import com.srs.live.entity.LiveRecord;
import com.srs.live.service.LiveRecordService;
import com.srs.live.service.OnlineUserService;
import com.srs.live.service.WebSocketService;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class LiveWebSocketHandler extends TextWebSocketHandler {

    private final JwtUtil jwtUtil;
    private final WebSocketSessionManager sessionManager;
    private final OnlineUserService onlineUserService;
    private final WebSocketService webSocketService;
    private final LiveRecordService liveRecordService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final String nodeId;

    public LiveWebSocketHandler(JwtUtil jwtUtil,
                                WebSocketSessionManager sessionManager,
                                OnlineUserService onlineUserService,
                                WebSocketService webSocketService,
                                LiveRecordService liveRecordService,
                                RedisTemplate<String, Object> redisTemplate,
                                ObjectMapper objectMapper,
                                @Value("${srs.node-id}") String nodeId) {
        this.jwtUtil = jwtUtil;
        this.sessionManager = sessionManager;
        this.onlineUserService = onlineUserService;
        this.webSocketService = webSocketService;
        this.liveRecordService = liveRecordService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.nodeId = nodeId;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = extractToken(session);
        if (token == null) {
            closeSession(session, "missing token");
            return;
        }

        try {
            Claims claims = jwtUtil.parseToken(token);
            String uid = claims.get("uid", String.class);
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);

            session.getAttributes().put("uid", uid);
            session.getAttributes().put("username", username);
            session.getAttributes().put("role", role);

            sessionManager.addSession(uid, session);
            log.info("WS connected: uid={}, username={}, role={}, sessionId={}", uid, username, role, session.getId());
        } catch (Exception e) {
            closeSession(session, "invalid token: " + e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String uid = (String) session.getAttributes().get("uid");
        if (uid == null) return;

        try {
            Map<String, Object> msg = objectMapper.readValue(message.getPayload(),
                    new TypeReference<Map<String, Object>>() {});
            String type = (String) msg.get("type");

            switch (type) {
                case WsMsgType.PING -> handlePing(uid, session);
                case WsMsgType.JOIN_ROOM -> handleJoinRoom(uid, session, msg);
                case WsMsgType.LEAVE_ROOM -> handleLeaveRoom(uid, session, msg);
                case WsMsgType.ACK -> handleAck(uid, msg);
                default -> log.warn("unknown ws message type: {} from uid={}", type, uid);
            }
        } catch (Exception e) {
            log.error("handle ws message error from uid={}", uid, e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String uid = (String) session.getAttributes().get("uid");
        if (uid == null) return;

        String roomId = sessionManager.getUserRoom(uid);

        // 记录参与者离开（若直播正在进行）
        Long liveRecordId = (Long) session.getAttributes().get("liveRecordId");
        if (liveRecordId != null) {
            liveRecordService.recordParticipantLeave(liveRecordId, uid);
        } else if (roomId != null) {
            LiveRecord activeRecord = liveRecordService.getActiveRecord(roomId);
            if (activeRecord != null) {
                liveRecordService.recordParticipantLeave(activeRecord.getId(), uid);
            }
        }

        sessionManager.removeSession(uid);
        sessionManager.leaveAllRooms(uid);

        log.info("WS disconnected: uid={}, roomId={}, status={}", uid, roomId, status);

        // 不清除在线状态，等心跳超时后再清理，兼容网络抖动
        // 但更新心跳时间，给予重连窗口
        onlineUserService.updateHeartbeat(uid);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String uid = (String) session.getAttributes().get("uid");
        log.error("WS transport error: uid={}", uid, exception);
    }

    private void handlePing(String uid, WebSocketSession session) {
        onlineUserService.updateHeartbeat(uid);
        try {
            Map<String, Object> pong = Map.of(
                    "type", WsMsgType.PONG,
                    "timestamp", System.currentTimeMillis()
            );
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
            }
        } catch (Exception e) {
            log.error("send pong error: uid={}", uid, e);
        }
    }

    private void handleJoinRoom(String uid, WebSocketSession session, Map<String, Object> msg) {
        Map<String, Object> data = (Map<String, Object>) msg.get("data");
        String roomId = (String) data.get("roomId");
        String username = (String) session.getAttributes().get("username");
        String role = (String) session.getAttributes().get("role");

        sessionManager.joinRoom(uid, roomId);

        // 记录在线状态
        onlineUserService.userOnline(uid, username, role, roomId, session.getId(), nodeId);

        // 广播在线列表更新（按主播/参与人员分类）
        var users = onlineUserService.getRoomOnlineUsersCategorized(roomId);
        webSocketService.broadcastRoomUserList(roomId, users);

        // 记录参与者进入（若直播正在进行）
        LiveRecord activeRecord = liveRecordService.getActiveRecord(roomId);
        if (activeRecord != null) {
            liveRecordService.recordParticipantJoin(activeRecord.getId(), uid, username);
            session.getAttributes().put("liveRecordId", activeRecord.getId());
        }

        log.info("user joined room: uid={}, roomId={}", uid, roomId);
    }

    private void handleLeaveRoom(String uid, WebSocketSession session, Map<String, Object> msg) {
        Map<String, Object> data = (Map<String, Object>) msg.get("data");
        String roomId = (String) data.get("roomId");

        // 记录参与者离开（若直播正在进行）
        Long liveRecordId = (Long) session.getAttributes().get("liveRecordId");
        if (liveRecordId != null) {
            liveRecordService.recordParticipantLeave(liveRecordId, uid);
            session.getAttributes().remove("liveRecordId");
        }

        sessionManager.leaveRoom(uid, roomId);
        onlineUserService.userOffline(uid);

        // 广播在线列表更新（按主播/参与人员分类）
        var users = onlineUserService.getRoomOnlineUsersCategorized(roomId);
        webSocketService.broadcastRoomUserList(roomId, users);

        log.info("user left room: uid={}, roomId={}", uid, roomId);
    }

    private void handleAck(String uid, Map<String, Object> msg) {
        // ACK 处理，用于管理指令确认
        Map<String, Object> data = (Map<String, Object>) msg.get("data");
        String ackMsgId = (String) data.get("ackMsgId");
        log.debug("ack received: uid={}, msgId={}", uid, ackMsgId);
    }

    private String extractToken(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        String query = uri.getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if ("token".equals(kv[0]) && kv.length == 2) {
                return kv[1];
            }
        }
        return null;
    }

    private void closeSession(WebSocketSession session, String reason) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION.withReason(reason));
        } catch (Exception e) {
            log.error("close session error", e);
        }
    }
}