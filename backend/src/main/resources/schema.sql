-- SRS 直播平台 数据库初始化脚本
-- 兼容 MySQL 8.0+

CREATE DATABASE IF NOT EXISTS srs_live DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE srs_live;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花算法ID',
    `uid` VARCHAR(64) NOT NULL UNIQUE COMMENT '业务唯一用户标识',
    `phone` VARCHAR(20) NOT NULL UNIQUE COMMENT '手机号（登录凭证）',
    `username` VARCHAR(100) DEFAULT '' COMMENT '昵称/显示名',
    `company` VARCHAR(200) DEFAULT '' COMMENT '医院/公司',
    `department` VARCHAR(200) DEFAULT '' COMMENT '部门/科室',
    `phone_tail` VARCHAR(10) DEFAULT '' COMMENT '手机尾号',
    `password_hash` VARCHAR(255) NOT NULL COMMENT '加密密码',
    `role` VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT '角色: user/admin',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-正常 0-禁用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_uid` (`uid`),
    INDEX `idx_phone` (`phone`),
    INDEX `idx_role` (`role`),
    UNIQUE INDEX `idx_user_identify` (`username`, `company`, `department`, `phone_tail`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 直播间表
CREATE TABLE IF NOT EXISTS `room` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `room_id` VARCHAR(64) NOT NULL UNIQUE COMMENT '业务唯一房间标识',
    `title` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '直播间标题',
    `publisher_uid` VARCHAR(64) NOT NULL COMMENT '主播uid',
    `status` VARCHAR(20) NOT NULL DEFAULT 'waiting' COMMENT '状态: waiting/live/closed',
    `srs_node` VARCHAR(64) NULL COMMENT '当前直播使用的SRS节点ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `started_at` DATETIME NULL COMMENT '最近开播时间',
    `closed_at` DATETIME NULL COMMENT '关播时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_room_id` (`room_id`),
    INDEX `idx_publisher` (`publisher_uid`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播间表';

-- 在线用户映射表
CREATE TABLE IF NOT EXISTS `online_user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `uid` VARCHAR(64) NOT NULL UNIQUE COMMENT '用户uid',
    `username` VARCHAR(100) NOT NULL COMMENT '用户名',
    `role` VARCHAR(20) NOT NULL COMMENT '角色',
    `room_id` VARCHAR(64) NOT NULL COMMENT '所在房间ID',
    `ws_session_id` VARCHAR(128) NULL COMMENT 'WebSocket会话ID',
    `srs_client_id` VARCHAR(64) NULL COMMENT 'SRS媒体连接client_id',
    `srs_node_id` VARCHAR(64) NULL COMMENT 'SRS节点ID',
    `last_heartbeat` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后心跳时间',
    `node_id` VARCHAR(64) NOT NULL COMMENT '所属WebSocket节点ID',
    `connected_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '连接建立时间',
    INDEX `idx_uid` (`uid`),
    INDEX `idx_room_id` (`room_id`),
    INDEX `idx_node_id` (`node_id`),
    INDEX `idx_srs_node` (`srs_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='在线用户映射表';

-- SRS节点表
CREATE TABLE IF NOT EXISTS `srs_node` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `node_id` VARCHAR(64) NOT NULL UNIQUE COMMENT '节点唯一标识',
    `ip` VARCHAR(64) NOT NULL COMMENT '节点IP地址',
    `api_port` INT NOT NULL DEFAULT 1985 COMMENT '1985 API端口',
    `rtc_port` INT NOT NULL DEFAULT 8000 COMMENT 'WebRTC端口',
    `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态: active/inactive/removing',
    `weight` INT NOT NULL DEFAULT 10 COMMENT '调度权重',
    `max_connections` INT NOT NULL DEFAULT 1000 COMMENT '最大连接数',
    `current_connections` INT NOT NULL DEFAULT 0 COMMENT '当前活跃连接数',
    `cpu_usage` DECIMAL(5,2) NULL COMMENT 'CPU使用率',
    `mem_usage` DECIMAL(5,2) NULL COMMENT '内存使用率',
    `last_heartbeat` DATETIME NULL COMMENT '最后心跳时间',
    `registered_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    INDEX `idx_node_id` (`node_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SRS节点表';

-- 第三方应用表
CREATE TABLE IF NOT EXISTS `third_party_app` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `app_id` VARCHAR(64) NOT NULL UNIQUE COMMENT '应用唯一标识',
    `app_name` VARCHAR(200) NOT NULL COMMENT '应用名称',
    `access_key` VARCHAR(128) NOT NULL UNIQUE COMMENT 'AK访问密钥',
    `secret_key` VARCHAR(255) NOT NULL COMMENT 'SK签名密钥(加密存储)',
    `ip_whitelist` TEXT NULL COMMENT 'IP白名单 JSON数组',
    `rate_limit` INT NOT NULL DEFAULT 1000 COMMENT '每分钟调用上限',
    `webhook_url` VARCHAR(500) NULL COMMENT '回调URL',
    `webhook_events` TEXT NULL COMMENT '订阅事件列表 JSON数组',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-启用 0-禁用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_app_id` (`app_id`),
    INDEX `idx_access_key` (`access_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方应用表';

-- 集群事件记录表
CREATE TABLE IF NOT EXISTS `cluster_event` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `node_id` VARCHAR(64) NOT NULL COMMENT '关联节点ID',
    `event_type` VARCHAR(50) NOT NULL COMMENT '事件类型: node_down/node_up/connection_fail/timeout/capacity_warning',
    `severity` VARCHAR(20) NOT NULL COMMENT '严重程度: info/warning/critical',
    `message` TEXT NOT NULL COMMENT '事件描述',
    `payload` TEXT NULL COMMENT '附加数据JSON',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_node_id` (`node_id`),
    INDEX `idx_event_type` (`event_type`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集群事件记录表';

-- 插入默认管理员用户 (密码: admin123)
INSERT INTO `user` (`uid`, `phone`, `username`, `password_hash`, `role`) VALUES
('uid_admin', '13800138000', 'admin', '$2a$10$8QNqSVZLN72lIy7vJTFDleLoVax1T0SSVJVxOxRmPykmH5gEPt0WW', 'admin')
ON DUPLICATE KEY UPDATE `username` = `username`;

-- 直播记录表
CREATE TABLE IF NOT EXISTS `live_record` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花算法ID',
    `room_id` VARCHAR(64) NOT NULL COMMENT '关联房间ID',
    `publisher_uid` VARCHAR(64) NOT NULL COMMENT '主播uid',
    `started_at` DATETIME NOT NULL COMMENT '开播时间',
    `ended_at` DATETIME NULL COMMENT '关播时间',
    `duration_seconds` INT NULL DEFAULT 0 COMMENT '直播时长(秒)',
    `recording_enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否开启录播: 1-开启 0-关闭',
    `recording_status` VARCHAR(20) NOT NULL DEFAULT 'none' COMMENT '录播状态: none/recording/uploading/done/failed',
    `recording_url` VARCHAR(500) NULL COMMENT '录播文件MinIO地址',
    `participant_count` INT NOT NULL DEFAULT 0 COMMENT '参与人数',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_room_id` (`room_id`),
    INDEX `idx_publisher` (`publisher_uid`),
    INDEX `idx_started_at` (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播记录表';

-- 直播参与者表
CREATE TABLE IF NOT EXISTS `live_participant` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `live_record_id` BIGINT NOT NULL COMMENT '关联直播记录ID',
    `uid` VARCHAR(64) NOT NULL COMMENT '用户uid',
    `username` VARCHAR(100) NOT NULL COMMENT '用户名',
    `joined_at` DATETIME NOT NULL COMMENT '进入时间',
    `left_at` DATETIME NULL COMMENT '离开时间',
    `duration_seconds` INT NULL DEFAULT 0 COMMENT '停留时长(秒)',
    INDEX `idx_record_id` (`live_record_id`),
    INDEX `idx_uid` (`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播参与者表';