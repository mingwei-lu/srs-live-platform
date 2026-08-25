package com.srs.live.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cluster_event")
public class ClusterEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String nodeId;
    private String eventType;
    private String severity;
    private String message;
    private String payload;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}