package com.srs.live.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("live_participant")
public class LiveParticipant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long liveRecordId;
    private String uid;
    private String username;
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
    private Integer durationSeconds;
}