package com.srs.live.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("live_record")
public class LiveRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String roomId;
    private String publisherUid;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer durationSeconds;
    private Integer recordingEnabled;
    private String recordingStatus;
    private String recordingUrl;
    private Integer participantCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}