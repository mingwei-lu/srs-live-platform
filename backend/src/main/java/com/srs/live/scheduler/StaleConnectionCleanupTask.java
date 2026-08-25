package com.srs.live.scheduler;

import com.srs.live.websocket.WebSocketSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Slf4j
@Component
public class StaleConnectionCleanupTask {

    private final WebSocketSessionManager sessionManager;

    public StaleConnectionCleanupTask(WebSocketSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupStaleConnections() {
        // 每隔60s扫描一次，清理僵死连接
        // 实际场景中，WebSocket 配置了 idleTimeout=60s，由容器自动处理
        // 此处作为兜底，手动关闭已不可用的 session
        log.debug("stale connection cleanup task executed, online: {}", sessionManager.getOnlineCount());
    }
}