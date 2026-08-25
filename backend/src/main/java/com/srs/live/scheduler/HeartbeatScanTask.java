package com.srs.live.scheduler;

import com.srs.live.common.constant.RedisKeys;
import com.srs.live.service.OnlineUserService;
import com.srs.live.service.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
public class HeartbeatScanTask {

    private final RedisTemplate<String, Object> redisTemplate;
    private final OnlineUserService onlineUserService;
    private final WebSocketService webSocketService;
    private final long heartbeatTimeout;

    public HeartbeatScanTask(RedisTemplate<String, Object> redisTemplate,
                             OnlineUserService onlineUserService,
                             WebSocketService webSocketService,
                             @Value("${srs.cluster.heartbeat-timeout:45000}") long heartbeatTimeout) {
        this.redisTemplate = redisTemplate;
        this.onlineUserService = onlineUserService;
        this.webSocketService = webSocketService;
        this.heartbeatTimeout = heartbeatTimeout;
    }

    @Scheduled(fixedRateString = "${srs.cluster.heartbeat-scan-interval:10000}")
    public void scanHeartbeatTimeout() {
        Set<Object> onlineUsers = redisTemplate.opsForSet().members(RedisKeys.ONLINE_USERS);
        if (onlineUsers == null || onlineUsers.isEmpty()) return;

        long now = System.currentTimeMillis();

        for (Object uidObj : onlineUsers) {
            String uid = uidObj.toString();
            String key = RedisKeys.userHeartbeat(uid);
            String lastHeartbeat = (String) redisTemplate.opsForValue().get(key);
            if (lastHeartbeat == null) continue;

            try {
                long lastTime = Long.parseLong(lastHeartbeat);
                if (now - lastTime > heartbeatTimeout) {
                    log.info("heartbeat timeout, user offline: uid={}, lastHeartbeat={}", uid, lastTime);
                    handleUserOffline(uid);
                }
            } catch (NumberFormatException e) {
                log.warn("invalid heartbeat value for uid={}", uid);
            }
        }
    }

    private void handleUserOffline(String uid) {
        try {
            // 清理在线状态
            onlineUserService.userOffline(uid);
            log.info("user offline cleaned: uid={}", uid);
        } catch (Exception e) {
            log.error("handle user offline error: uid={}", uid, e);
        }
    }
}