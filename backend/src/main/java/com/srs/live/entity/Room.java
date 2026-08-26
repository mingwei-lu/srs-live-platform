package com.srs.live.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("room")
public class Room {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roomId;
    private String title;
    private String publisherUid;
    private String status;
    /** 节点类型: live(直播节点) 或 record(录制节点) */
    private String nodeType;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime closedAt;
    private String srsNode;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}