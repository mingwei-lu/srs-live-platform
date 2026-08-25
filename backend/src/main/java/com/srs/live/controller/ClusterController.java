package com.srs.live.controller;

import com.srs.live.dto.response.ApiResponse;
import com.srs.live.entity.ClusterEvent;
import com.srs.live.entity.SrsNode;
import com.srs.live.service.ClusterService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/v")
public class ClusterController {

    private final ClusterService clusterService;

    public ClusterController(ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.success(clusterService.getClusterOverview());
    }

    // ========== 节点管理（只读，节点由 srs-proxy 自动注册到 Redis）==========

    @GetMapping("/nodes")
    public ApiResponse<List<SrsNode>> listNodes() {
        return ApiResponse.success(clusterService.getAllNodes());
    }

    @GetMapping("/nodes/{nodeId}")
    public ApiResponse<SrsNode> getNode(@PathVariable String nodeId) {
        return ApiResponse.success(clusterService.getNode(nodeId));
    }

    // ========== 健康检查 ==========

    @GetMapping("/nodes/{nodeId}/health")
    public ApiResponse<Map<String, Object>> checkNodeHealth(@PathVariable String nodeId) {
        return ApiResponse.success(clusterService.checkNodeHealth(nodeId));
    }

    @GetMapping("/nodes/health/all")
    public ApiResponse<List<Map<String, Object>>> checkAllNodesHealth() {
        return ApiResponse.success(clusterService.checkAllNodesHealth());
    }

    @GetMapping("/events")
    public ApiResponse<List<ClusterEvent>> getEvents(@RequestParam(required = false) String type) {
        return ApiResponse.success(clusterService.getClusterEvents(type));
    }
}
