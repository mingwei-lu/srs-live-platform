package com.srs.live.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {

    // uid -> WebSocketSession
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // roomId -> Set<uid>
    private final ConcurrentHashMap<String, Set<String>> roomUsers = new ConcurrentHashMap<>();

    // uid -> roomId
    private final ConcurrentHashMap<String, String> userRoom = new ConcurrentHashMap<>();

    public void addSession(String uid, WebSocketSession session) {
        sessions.put(uid, session);
    }

    public void removeSession(String uid) {
        sessions.remove(uid);
    }

    public WebSocketSession getSession(String uid) {
        return sessions.get(uid);
    }

    public boolean hasSession(String uid) {
        return sessions.containsKey(uid);
    }

    public void joinRoom(String uid, String roomId) {
        userRoom.put(uid, roomId);
        roomUsers.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(uid);
    }

    public void leaveRoom(String uid, String roomId) {
        userRoom.remove(uid);
        Set<String> users = roomUsers.get(roomId);
        if (users != null) {
            users.remove(uid);
            if (users.isEmpty()) {
                roomUsers.remove(roomId);
            }
        }
    }

    public void leaveAllRooms(String uid) {
        String roomId = userRoom.remove(uid);
        if (roomId != null) {
            Set<String> users = roomUsers.get(roomId);
            if (users != null) {
                users.remove(uid);
                if (users.isEmpty()) {
                    roomUsers.remove(roomId);
                }
            }
        }
    }

    public Set<String> getRoomUsers(String roomId) {
        Set<String> users = roomUsers.get(roomId);
        return users != null ? users : Collections.emptySet();
    }

    public String getUserRoom(String uid) {
        return userRoom.get(uid);
    }

    public int getOnlineCount() {
        return sessions.size();
    }
}