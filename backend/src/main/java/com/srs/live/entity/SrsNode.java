package com.srs.live.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("srs_node")
public class SrsNode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String nodeId;
    private String ip;
    private Integer apiPort;
    private Integer rtcPort;
    private String status;
    private Integer weight;
    private Integer maxConnections;
    private Integer currentConnections;
    private BigDecimal cpuUsage;
    private BigDecimal memUsage;
    private LocalDateTime lastHeartbeat;
    private LocalDateTime registeredAt;
}