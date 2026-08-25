package com.srs.live.scheduler;

import com.srs.live.common.util.SrsApiClient;
import com.srs.live.entity.SrsNode;
import com.srs.live.mapper.SrsNodeMapper;
import com.srs.live.service.ClusterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ClusterHealthCheckTask {

    private final SrsNodeMapper srsNodeMapper;
    private final SrsApiClient srsApiClient;
    private final ClusterService clusterService;

    private static final int MAX_FAILURES = 3;

    public ClusterHealthCheckTask(SrsNodeMapper srsNodeMapper,
                                  SrsApiClient srsApiClient,
                                  ClusterService clusterService) {
        this.srsNodeMapper = srsNodeMapper;
        this.srsApiClient = srsApiClient;
        this.clusterService = clusterService;
    }

    @Scheduled(fixedRateString = "${srs.cluster.health-check-interval:10000}")
    public void healthCheck() {
        List<SrsNode> nodes = srsNodeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SrsNode>()
                        .ne(SrsNode::getStatus, "removing"));
        for (SrsNode node : nodes) {
            boolean alive = srsApiClient.healthCheck(node.getIp(), node.getApiPort());
            if (!alive) {
                log.warn("SRS node health check failed: nodeId={}, ip={}", node.getNodeId(), node.getIp());
                node.setStatus("inactive");
                srsNodeMapper.updateById(node);
                clusterService.recordEvent(node.getNodeId(), "node_down", "critical",
                        "Node " + node.getNodeId() + " health check failed");
            } else {
                if ("inactive".equals(node.getStatus())) {
                    node.setStatus("active");
                    srsNodeMapper.updateById(node);
                    clusterService.recordEvent(node.getNodeId(), "node_up", "info",
                            "Node " + node.getNodeId() + " recovered");
                    log.info("SRS node recovered: nodeId={}", node.getNodeId());
                }
            }
        }
    }
}