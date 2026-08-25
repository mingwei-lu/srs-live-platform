package com.srs.live.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srs.live.common.constant.RedisKeys;
import com.srs.live.common.constant.WsMsgType;
import com.srs.live.websocket.WebSocketSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class WebSocketService {

    private final WebSocketSessionManager sessionManager;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public WebSocketService(WebSocketSessionManager sessionManager,
                            RedisTemplate<String, Object> redisTemplate,
                            ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendMessage(String uid, String message) {
        WebSocketSession session = sessionManager.getSession(uid);
        if (session != null && session.isOpen()) {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (Exception e) {
                log.error("send message to {} failed", uid, e);
            }
        }
    }

    public void broadcastToRoom(String roomId, String message) {
        // 本地广播
        Set<String> localUsers = sessionManager.getRoomUsers(roomId);
        for (String uid : localUsers) {
            sendMessage(uid, message);
        }

        // 跨节点广播（Redis PubSub）
        Map<String, Object> pubMsg = new HashMap<>();
        pubMsg.put("type", "room_broadcast");
        pubMsg.put("roomId", roomId);
        pubMsg.put("message", message);
        try {
            redisTemplate.convertAndSend("ws:room:broadcast", objectMapper.writeValueAsString(pubMsg));
        } catch (JsonProcessingException e) {
            log.error("redis pub msg error", e);
        }
    }

    public void sendKickNotification(String uid, String reason, String operator) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", WsMsgType.KICK_NOTIFICATION);
        Map<String, Object> body = new HashMap<>();
        body.put("reason", reason);
        body.put("operator", operator);
        data.put("data", body);
        data.put("msgId", "kick_" + UUID.randomUUID().toString().substring(0, 8));
        data.put("timestamp", System.currentTimeMillis());

        try {
            String msg = objectMapper.writeValueAsString(data);
            sendMessage(uid, msg);
        } catch (JsonProcessingException e) {
            log.error("send kick notification failed", e);
        }
    }

    public void broadcastRoomStatusChange(String roomId, String status) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", WsMsgType.ROOM_STATUS_CHANGE);
        Map<String, Object> body = new HashMap<>();
        body.put("roomId", roomId);
        body.put("status", status);
        data.put("data", body);
        data.put("timestamp", System.currentTimeMillis());

        try {
            String msg = objectMapper.writeValueAsString(data);
            broadcastToRoom(roomId, msg);
        } catch (JsonProcessingException e) {
            log.error("broadcast room status change failed", e);
        }
    }

    public void broadcastRoomUserList(String roomId, List<Map<String, Object>> users) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", WsMsgType.ROOM_USER_LIST);
        Map<String, Object> body = new HashMap<>();
        body.put("roomId", roomId);
        body.put("users", users);
        body.put("onlineCount", users.size());
        data.put("data", body);
        data.put("timestamp", System.currentTimeMillis());

        try {
            String msg = objectMapper.writeValueAsString(data);
            broadcastToRoom(roomId, msg);
        } catch (JsonProcessingException e) {
            log.error("broadcast room user list failed", e);
        }
    }

    public void storeOfflineMessage(String uid, String message) {
        String key = RedisKeys.userOfflineMsgs(uid);
        redisTemplate.opsForList().leftPush(key, message);
        redisTemplate.expire(key, 300, TimeUnit.SECONDS);
    }

    public void flushOfflineMessages(String uid) {
        String key = RedisKeys.userOfflineMsgs(uid);
        List<Object> msgs = redisTemplate.opsForList().range(key, 0, -1);
        if (msgs != null) {
            for (Object msg : msgs) {
                sendMessage(uid, msg.toString());
            }
            redisTemplate.delete(key);
        }
    }
}