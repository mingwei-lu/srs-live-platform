package com.srs.live.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.srs.live.common.constant.RedisKeys;
import com.srs.live.common.exception.BusinessException;
import com.srs.live.common.util.JwtUtil;
import com.srs.live.entity.ClusterEvent;
import com.srs.live.entity.SrsNode;
import com.srs.live.mapper.ClusterEventMapper;
import com.srs.live.mapper.SrsNodeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ClusterService {

    private final SrsNodeMapper srsNodeMapper;
    private final ClusterEventMapper clusterEventMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtil jwtUtil;

    @Value("${srs.srs.entrypoint:}")
    private String srsEntrypoint;

    @Value("${srs.srs.rtc-port:8000}")
    private int srsRtcPort;

    public ClusterService(SrsNodeMapper srsNodeMapper,
                          ClusterEventMapper clusterEventMapper,
                          RedisTemplate<String, Object> redisTemplate,
                          JwtUtil jwtUtil) {
        this.srsNodeMapper = srsNodeMapper;
        this.clusterEventMapper = clusterEventMapper;
        this.redisTemplate = redisTemplate;
        this.jwtUtil = jwtUtil;
    }

    public void registerNode(SrsNode node) {
        srsNodeMapper.insert(node);
        redisTemplate.opsForZSet().add(RedisKeys.SRS_CLUSTER_NODES, node.getNodeId(), 0);
        String connKey = RedisKeys.srsNodeConnections(node.getNodeId());
        redisTemplate.opsForValue().set(connKey, "0", 30, TimeUnit.SECONDS);
        log.info("SRS node registered: {}", node.getNodeId());
    }

    public void unregisterNode(String nodeId) {
        LambdaQueryWrapper<SrsNode> queryWrapper = new LambdaQueryWrapper<>();
        srsNodeMapper.delete(queryWrapper.eq(SrsNode::getNodeId, nodeId));
        redisTemplate.opsForZSet().remove(RedisKeys.SRS_CLUSTER_NODES, nodeId);
        log.info("SRS node unregistered: {}", nodeId);
    }

    public String selectOptimalNode(String roomId) {
        Set<Object> nodes = redisTemplate.opsForZSet().range(RedisKeys.SRS_CLUSTER_NODES, 0, -1);
        if (nodes == null || nodes.isEmpty()) return null;

        String bestNode = null;
        int minConnections = Integer.MAX_VALUE;

        for (Object nodeObj : nodes) {
            String nodeId = nodeObj.toString();
            String connStr = (String) redisTemplate.opsForValue().get(RedisKeys.srsNodeConnections(nodeId));
            int connections = connStr != null ? Integer.parseInt(connStr) : 0;
            if (connections < minConnections) {
                minConnections = connections;
                bestNode = nodeId;
            }
        }
        return bestNode;
    }

    public String generateWhipUrl(String nodeId, String streamId, String uid) {
        if (srsEntrypoint != null && !srsEntrypoint.isBlank()) {
            String token = jwtUtil.generateToken(uid, "", "publisher");
            return String.format("https://%s:%d/rtc/v1/whip/?token=%s&app=live&stream=%s",
                    srsEntrypoint, srsRtcPort, token, streamId);
        }
        LambdaQueryWrapper<SrsNode> queryWrapper = new LambdaQueryWrapper<>();
        SrsNode node = srsNodeMapper.selectOne(
                queryWrapper.eq(SrsNode::getNodeId, nodeId));
        if (node == null) return null;

        String token = jwtUtil.generateToken(uid, "", "publisher");
        return String.format("https://%s:%d/rtc/v1/whip/?token=%s&app=live&stream=%s",
                node.getIp(), node.getRtcPort(), token, streamId);
    }

    public String generateWhepUrl(String nodeId, String streamId, String uid) {
        if (srsEntrypoint != null && !srsEntrypoint.isBlank()) {
            String token = jwtUtil.generateToken(uid, "", "audience");
            return String.format("https://%s:%d/rtc/v1/whep/?token=%s&app=live&stream=%s",
                    srsEntrypoint, srsRtcPort, token, streamId);
        }
        LambdaQueryWrapper<SrsNode> queryWrapper = new LambdaQueryWrapper<>();
        SrsNode node = srsNodeMapper.selectOne(
                queryWrapper.eq(SrsNode::getNodeId, nodeId));
        if (node == null) return null;

        String token = jwtUtil.generateToken(uid, "", "audience");
        return String.format("https://%s:%d/rtc/v1/whep/?token=%s&app=live&stream=%s",
                node.getIp(), node.getRtcPort(), token, streamId);
    }

    public List<SrsNode> getAllNodes() {
        // 从 Redis ZSet 获取节点ID（由 srs-proxy 自动注册）
        Set<Object> nodeIds = redisTemplate.opsForZSet().range(RedisKeys.SRS_CLUSTER_NODES, 0, -1);
        if (nodeIds == null || nodeIds.isEmpty()) {
            return List.of();
        }
        List<SrsNode> nodes = new ArrayList<>();
        for (Object nodeIdObj : nodeIds) {
            String nodeId = nodeIdObj.toString();
            SrsNode node = getNode(nodeId);
            if (node != null) {
                // 从 Redis 获取实时连接数
                String connStr = (String) redisTemplate.opsForValue().get(RedisKeys.srsNodeConnections(nodeId));
                if (connStr != null) {
                    try {
                        node.setCurrentConnections(Integer.parseInt(connStr));
                    } catch (NumberFormatException ignored) {}
                }
                nodes.add(node);
            }
        }
        return nodes;
    }

    public SrsNode getNode(String nodeId) {
        LambdaQueryWrapper<SrsNode> queryWrapper = new LambdaQueryWrapper<>();
        return srsNodeMapper.selectOne(
                queryWrapper.eq(SrsNode::getNodeId, nodeId));
    }

    public void updateNodeHeartbeat(String nodeId, int currentConnections, BigDecimal cpuUsage, BigDecimal memUsage) {
        SrsNode node = getNode(nodeId);
        if (node != null) {
            node.setCurrentConnections(currentConnections);
            node.setCpuUsage(cpuUsage);
            node.setMemUsage(memUsage);
            node.setLastHeartbeat(LocalDateTime.now());
            srsNodeMapper.updateById(node);

            String connKey = RedisKeys.srsNodeConnections(nodeId);
            redisTemplate.opsForValue().set(connKey, String.valueOf(currentConnections), 30, TimeUnit.SECONDS);
            redisTemplate.opsForZSet().add(RedisKeys.SRS_CLUSTER_NODES, nodeId, currentConnections);
        }
    }

    public void recordEvent(String nodeId, String eventType, String severity, String message) {
        ClusterEvent event = new ClusterEvent();
        event.setNodeId(nodeId);
        event.setEventType(eventType);
        event.setSeverity(severity);
        event.setMessage(message);
        clusterEventMapper.insert(event);
    }

    public Map<String, Object> getClusterOverview() {
        Map<String, Object> overview = new HashMap<>();
        List<SrsNode> nodes = getAllNodes();

        long totalNodes = nodes.size();
        long onlineNodes = nodes.stream().filter(n -> "active".equals(n.getStatus())).count();
        long totalStreams = nodes.stream().mapToInt(SrsNode::getCurrentConnections).sum();

        overview.put("totalNodes", totalNodes);
        overview.put("onlineNodes", onlineNodes);
        overview.put("offlineNodes", totalNodes - onlineNodes);
        overview.put("totalConnections", totalStreams);
        overview.put("onlineUsers", getOnlineUserCount());
        return overview;
    }

    private long getOnlineUserCount() {
        Long count = redisTemplate.opsForSet().size(RedisKeys.ONLINE_USERS);
        return count != null ? count : 0;
    }

    public List<ClusterEvent> getClusterEvents(String type) {
        var qw = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ClusterEvent>();
        if (type != null && !type.isEmpty()) {
            qw.eq(ClusterEvent::getEventType, type);
        }
        qw.orderByDesc(ClusterEvent::getCreatedAt).last("LIMIT 100");
        return clusterEventMapper.selectList(qw);
    }

    // ========== 节点管理 ==========

    /** 添加 SRS 节点 */
    public SrsNode addNode(SrsNode node) {
        if (node.getNodeId() == null || node.getNodeId().isBlank()) {
            throw new BusinessException(40001, "nodeId is required");
        }
        if (getNode(node.getNodeId()) != null) {
            throw new BusinessException(40002, "node already exists");
        }
        node.setStatus("active");
        node.setCurrentConnections(0);
        node.setCpuUsage(BigDecimal.ZERO);
        node.setMemUsage(BigDecimal.ZERO);
        node.setLastHeartbeat(LocalDateTime.now());
        node.setRegisteredAt(LocalDateTime.now());
        srsNodeMapper.insert(node);
        redisTemplate.opsForZSet().add(RedisKeys.SRS_CLUSTER_NODES, node.getNodeId(), 0);
        log.info("SRS node added: {} at {}", node.getNodeId(), node.getIp());
        return node;
    }

    /** 删除 SRS 节点 */
    public void removeNode(String nodeId) {
        unregisterNode(nodeId);
    }

    /** 更新 SRS 节点 */
    public void updateNode(SrsNode node) {
        if (node.getNodeId() == null) {
            throw new BusinessException(40001, "nodeId is required");
        }
        srsNodeMapper.updateById(node);
    }

    // ========== 健康检查 ==========

    /** 检查 SRS 节点健康状态 */
    public Map<String, Object> checkNodeHealth(String nodeId) {
        SrsNode node = getNode(nodeId);
        if (node == null) {
            throw new BusinessException(40004, "node not found");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodeId", nodeId);
        result.put("ip", node.getIp());
        result.put("apiPort", node.getApiPort());

        String healthUrl = String.format("http://%s:%d/api/v1/servers", node.getIp(), node.getApiPort());
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().add(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter());

        try {
            // 尝试访问 SRS HTTP API
            URI uri = URI.create(healthUrl);
            var response = restTemplate.getForEntity(uri, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                result.put("status", "healthy");
                result.put("srsResponse", body);
                node.setStatus("active");
            } else {
                result.put("status", "unhealthy");
                result.put("reason", "SRS API returned non-2xx status");
                node.setStatus("inactive");
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.warn("SRS node {} unreachable: {}", nodeId, e.getMessage());
            result.put("status", "unreachable");
            result.put("reason", "Cannot connect to SRS API: " + e.getMessage());
            node.setStatus("inactive");
        } catch (Exception e) {
            log.warn("SRS node {} health check failed: {}", nodeId, e.getMessage());
            result.put("status", "error");
            result.put("reason", e.getMessage());
            node.setStatus("inactive");
        }

        node.setLastHeartbeat(LocalDateTime.now());
        srsNodeMapper.updateById(node);
        return result;
    }

    /** 批量检查所有节点健康 */
    public List<Map<String, Object>> checkAllNodesHealth() {
        List<SrsNode> nodes = getAllNodes();
        List<Map<String, Object>> results = new ArrayList<>();
        for (SrsNode node : nodes) {
            try {
                Map<String, Object> health = checkNodeHealth(node.getNodeId());
                results.add(health);
            } catch (Exception e) {
                log.error("Health check failed for node {}: {}", node.getNodeId(), e.getMessage());
                Map<String, Object> error = new HashMap<>();
                error.put("nodeId", node.getNodeId());
                error.put("status", "error");
                error.put("reason", e.getMessage());
                results.add(error);
            }
        }
        return results;
    }
}