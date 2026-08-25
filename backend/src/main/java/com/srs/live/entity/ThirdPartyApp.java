package com.srs.live.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("third_party_app")
public class ThirdPartyApp {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appId;
    private String appName;
    private String accessKey;
    private String secretKey;
    private String ipWhitelist;
    private Integer rateLimit;
    private String webhookUrl;
    private String webhookEvents;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}