package com.srs.live.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("online_user")
public class OnlineUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uid;
    private String username;
    private String role;
    private String roomId;
    private String wsSessionId;
    private String srsClientId;
    private String srsNodeId;
    private LocalDateTime lastHeartbeat;
    private String nodeId;
    private LocalDateTime connectedAt;
}