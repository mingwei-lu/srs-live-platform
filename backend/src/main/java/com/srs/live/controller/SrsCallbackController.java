package com.srs.live.controller;

import com.srs.live.common.util.JwtUtil;
import com.srs.live.dto.request.SrsCallbackRequest;
import com.srs.live.service.OnlineUserService;
import com.srs.live.service.RoomService;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/srs/callback")
public class SrsCallbackController {

    private final JwtUtil jwtUtil;
    private final OnlineUserService onlineUserService;
    private final RoomService roomService;

    public SrsCallbackController(JwtUtil jwtUtil, OnlineUserService onlineUserService,
                                 RoomService roomService) {
        this.jwtUtil = jwtUtil;
        this.onlineUserService = onlineUserService;
        this.roomService = roomService;
    }

    @PostMapping("/on_publish")
    public ResponseEntity<String> onPublish(@RequestBody SrsCallbackRequest request) {
        log.info("SRS on_publish: clientId={}, stream={}, param={}", request.getClientId(), request.getStream(), request.getParam());
        try {
            String token = extractToken(request.getParam());
            if (token == null) return ResponseEntity.status(403).body("403 forbidden: no token");

            Claims claims = jwtUtil.parseToken(token);
            String uid = claims.get("uid", String.class);

            // stream 参数即为房间 ID，推流者自动成为该房间主播（不做强制预设校验）
            String roomId = request.getStream();
            var room = roomService.getRoomEntity(roomId);
            room.setPublisherUid(uid);
            roomService.updatePublisher(room);

            onlineUserService.bindSrsClient(uid, request.getClientId(), request.getSrsNodeId());
            log.info("on_publish success: uid={}, clientId={}, roomId={}, 主播已更新为当前推流者", uid, request.getClientId(), roomId);
            return ResponseEntity.ok("200");
        } catch (Exception e) {
            log.error("on_publish error", e);
            return ResponseEntity.status(403).body("403 forbidden: " + e.getMessage());
        }
    }

    @PostMapping("/on_unpublish")
    public ResponseEntity<String> onUnpublish(@RequestBody SrsCallbackRequest request) {
        log.info("SRS on_unpublish: clientId={}", request.getClientId());
        return ResponseEntity.ok("200");
    }

    @PostMapping("/on_play")
    public ResponseEntity<String> onPlay(@RequestBody SrsCallbackRequest request) {
        log.info("SRS on_play: clientId={}, stream={}, param={}", request.getClientId(), request.getStream(), request.getParam());
        try {
            String token = extractToken(request.getParam());
            if (token == null) return ResponseEntity.status(403).body("403 forbidden: no token");

            Claims claims = jwtUtil.parseToken(token);
            String uid = claims.get("uid", String.class);

            // 任何已认证用户均可拉流观看
            onlineUserService.bindSrsClient(uid, request.getClientId(), request.getSrsNodeId());
            log.info("on_play success: uid={}, clientId={}", uid, request.getClientId());
            return ResponseEntity.ok("200");
        } catch (Exception e) {
            log.error("on_play error", e);
            return ResponseEntity.status(403).body("403 forbidden: " + e.getMessage());
        }
    }

    @PostMapping("/on_stop")
    public ResponseEntity<String> onStop(@RequestBody SrsCallbackRequest request) {
        log.info("SRS on_stop: clientId={}", request.getClientId());
        return ResponseEntity.ok("200");
    }

    private String extractToken(String param) {
        if (param == null) return null;
        for (String part : param.split("&")) {
            String[] kv = part.split("=", 2);
            if ("token".equals(kv[0]) && kv.length == 2) {
                return kv[1];
            }
        }
        return null;
    }
}