# SRS 流媒体直播平台 — 低层设计文档 (LLD)

## 修订记录

| 版本 | 日期 | 修订内容 |
|------|------|----------|
| v1.0 | 2026-08-24 | 初始版本，完成全部低层设计 |

---

## 一、项目结构与模块划分

### 1.1 Maven 模块结构

```
srs-live-platform/
├── srs-common/                    # 公共模块：工具类、常量、异常
├── srs-dal/                       # 数据访问层：MyBatis Mapper、Entity
├── srs-service/                   # 业务服务层：核心业务逻辑
├── srs-api/                       # REST API 层：对外请求入口
├── srs-websocket/                 # WebSocket 信令服务
├── srs-cluster/                   # SRS 集群管理模块
├── srs-third-party/               # 第三方 API 网关服务
├── srs-task/                      # 定时任务模块
└── srs-bootstrap/                 # 启动入口
```

### 1.2 包结构

```
com.srs.live
├── common
│   ├── config          # 全局配置类
│   ├── constant        # 常量定义
│   ├── exception       # 全局异常
│   ├── model           # 通用 VO/Query
│   └── util            # 工具类
├── dal
│   ├── entity          # 数据实体
│   ├── mapper          # MyBatis Mapper
│   └── redis           # Redis 操作
├── service
│   ├── auth            # 鉴权服务
│   ├── room            # 直播间服务
│   ├── user            # 用户服务
│   ├── heartbeat       # 心跳服务
│   └── stream          # 流管理服务
├── api
│   ├── controller      # REST 控制器
│   ├── dto             # 请求/响应 DTO
│   ├── interceptor     # 拦截器（鉴权、限流、日志）
│   └── websocket       # WebSocket 端点
├── websocket
│   ├── handler         # 消息处理器
│   ├── session         # 会话管理
│   └── cluster         # 集群消息广播
├── cluster
│   ├── manager         # SRS 节点管理器
│   ├── scheduler       # 调度策略
│   └── monitor         # 健康检查与监控
├── thirdparty
│   ├── controller      # 第三方 API 控制器
│   ├── auth            # AK/SK 鉴权
│   └── webhook         # Webhook 回调
└── task
    ├── heartbeat       # 心跳超时扫描任务
    └── cleanup         # 数据清理任务
```

## 二、数据库设计

### 2.1 表结构

#### 用户表 (user)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 自增主键 |
| `uid` | VARCHAR(64) | UNIQUE, NOT NULL | 业务唯一用户标识 |
| `username` | VARCHAR(100) | UNIQUE, NOT NULL | 用户名 |
| `password_hash` | VARCHAR(255) | NOT NULL | 加密密码 |
| `role` | VARCHAR(20) | NOT NULL, DEFAULT 'audience' | 用户角色：publisher / audience / admin |
| `status` | TINYINT | NOT NULL, DEFAULT 1 | 状态：1-正常，0-禁用 |
| `created_at` | DATETIME | NOT NULL | 创建时间 |
| `updated_at` | DATETIME | NOT NULL, ON UPDATE | 更新时间 |

#### 直播间表 (room)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 自增主键 |
| `room_id` | VARCHAR(64) | UNIQUE, NOT NULL | 业务唯一房间标识 |
| `title` | VARCHAR(200) | NOT NULL | 直播间标题 |
| `publisher_uid` | VARCHAR(64) | NOT NULL | 主播 uid |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'waiting' | 直播间状态：waiting / live / closed |
| `created_at` | DATETIME | NOT NULL | 创建时间 |
| `started_at` | DATETIME | NULL | 最近开播时间 |
| `closed_at` | DATETIME | NULL | 关播时间 |
| `updated_at` | DATETIME | NOT NULL, ON UPDATE | 更新时间 |

#### 在线用户表 (online_user)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 自增主键 |
| `uid` | VARCHAR(64) | UNIQUE, NOT NULL | 用户 uid |
| `username` | VARCHAR(100) | NOT NULL | 用户名 |
| `role` | VARCHAR(20) | NOT NULL | 角色 |
| `room_id` | VARCHAR(64) | NOT NULL | 所在房间 ID |
| `ws_session_id` | VARCHAR(128) | NULL | WebSocket 会话 ID |
| `srs_client_id` | VARCHAR(64) | NULL | SRS client_id |
| `last_heartbeat` | DATETIME | NOT NULL | 最后心跳时间 |
| `node_id` | VARCHAR(64) | NOT NULL | 所属节点 ID |
| `connected_at` | DATETIME | NOT NULL | 连接时间 |

#### SRS 节点表 (srs_node)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 自增主键 |
| `node_id` | VARCHAR(64) | UNIQUE, NOT NULL | 节点唯一标识 |
| `ip` | VARCHAR(64) | NOT NULL | 节点 IP |
| `api_port` | INT | NOT NULL | 1985 API 端口 |
| `rtc_port` | INT | NOT NULL | WebRTC 端口 |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'active' | 节点状态 |
| `weight` | INT | NOT NULL, DEFAULT 10 | 调度权重 |
| `max_connections` | INT | NOT NULL, DEFAULT 1000 | 最大连接数 |
| `current_connections` | INT | NOT NULL, DEFAULT 0 | 当前连接数 |
| `cpu_usage` | DECIMAL(5,2) | NULL | CPU 使用率 |
| `mem_usage` | DECIMAL(5,2) | NULL | 内存使用率 |
| `last_heartbeat` | DATETIME | NULL | 最后心跳时间 |
| `registered_at` | DATETIME | NOT NULL | 注册时间 |

### 2.2 Redis 缓存设计

| Key 模式 | 类型 | 说明 | TTL |
|----------|------|------|-----|
| `online:users` | Set | 全局在线用户 uid 集合 | 无 |
| `room:{room_id}:users` | Set | 房间内在线用户 uid 集合 | 无 |
| `user:{uid}:node` | String | 用户路由节点映射 | 60s |
| `user:{uid}:heartbeat` | String | 用户最后心跳时间戳 | 60s |
| `user:{uid}:offline_msgs` | List | 离线消息队列 | 300s |
| `ws:node:{node_id}:users` | Set | 节点上所有在线用户 uid | 无 |
| `srs:cluster:nodes` | ZSet | SRS 集群节点列表 | 无 |
| `srs:node:{node_id}:connections` | String | 节点当前活跃连接数 | 30s |

## 三、REST API 接口设计

### 3.1 用户模块

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/api/v1/users/register` | 用户注册 | 无 |
| POST | `/api/v1/users/login` | 用户登录，返回 JWT | 无 |
| GET | `/api/v1/users/{uid}` | 查询用户信息 | JWT |
| PUT | `/api/v1/users/{uid}` | 更新用户信息 | JWT |
| DELETE | `/api/v1/users/{uid}` | 禁用用户 | Admin |

#### 登录请求

```json
POST /api/v1/users/login
{
    "username": "streamer_zhang",
    "password": "encrypted_password"
}
```

#### 登录响应

```json
{
    "code": 0,
    "message": "success",
    "data": {
        "uid": "uid_1234567890",
        "username": "streamer_zhang",
        "role": "publisher",
        "token": "eyJhbGciOiJIUzI1NiIs..."
    }
}
```

### 3.2 直播间模块

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/api/v1/rooms` | 创建直播间 | JWT (publisher) |
| GET | `/api/v1/rooms/{roomId}` | 查询直播间详情 | JWT |
| GET | `/api/v1/rooms` | 查询直播间列表 | JWT |
| PUT | `/api/v1/rooms/{roomId}` | 修改直播间信息 | JWT (owner/admin) |
| DELETE | `/api/v1/rooms/{roomId}` | 关闭直播间 | JWT (owner/admin) |
| GET | `/api/v1/rooms/{roomId}/users` | 查询在线用户列表 | JWT (owner/admin) |
| POST | `/api/v1/rooms/{roomId}/start` | 开始直播，返回 WHIP 推流地址 | JWT (publisher) |
| POST | `/api/v1/rooms/{roomId}/stop` | 结束直播 | JWT (owner/admin) |
| POST | `/api/v1/rooms/{roomId}/kick/{uid}` | 踢出用户 | JWT (owner/admin) |

#### 开播请求

```json
POST /api/v1/rooms/{roomId}/start
Authorization: Bearer {jwt_token}
```

#### 开播响应

```json
{
    "code": 0,
    "message": "success",
    "data": {
        "whip_url": "https://srs-node1.live.com:1985/rtc/v1/whip/?token=xxx&app=live&stream=stream_xxx",
        "stream_id": "stream_xxx",
        "srs_node": "srs-node-1"
    }
}
```

### 3.3 流管理模块

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/api/v1/streams` | 查询所有流状态 | Admin |
| DELETE | `/api/v1/streams/{clientId}` | 断开指定流 | Admin |

### 3.4 SRS 回调接口

SRS 通过 http_hook 回调以下接口，所有回调均为 POST 请求：

| 路径 | 对应 SRS 事件 | 说明 |
|------|---------------|------|
| `/api/v1/srs/callback/on_publish` | on_publish | 推流开始 |
| `/api/v1/srs/callback/on_unpublish` | on_unpublish | 推流结束 |
| `/api/v1/srs/callback/on_play` | on_play | 拉流开始 |
| `/api/v1/srs/callback/on_stop` | on_stop | 拉流结束 |

#### on_publish 回调处理

```java
@PostMapping("/api/v1/srs/callback/on_publish")
public ResponseEntity<String> onPublish(@RequestBody SrsCallbackRequest request) {
    // 1. 从 query 参数中提取 token
    String token = parseToken(request.getQuery());
    // 2. 解析 JWT 获取用户信息
    Claims claims = jwtUtil.parseToken(token);
    // 3. 校验角色（必须是 publisher）
    if (!"publisher".equals(claims.get("role"))) {
        return ResponseEntity.status(403).body("forbidden");
    }
    // 4. 记录 client_id 与 uid 映射
    onlineUserService.bindSrsClient(claims.get("uid"), request.getClientId());
    // 5. 返回 200 允许推流
    return ResponseEntity.ok("200");
}
```

## 四、WebSocket 信令协议

### 4.1 连接建立

```
WebSocket URL: wss://ws.live.com/ws?token={jwt_token}
```

握手阶段校验 JWT，非法或过期 token 直接拒绝连接。

### 4.2 消息协议

所有消息采用 JSON 格式，统一消息结构：

```json
{
    "type": "消息类型",
    "data": {},
    "msgId": "消息唯一ID",
    "timestamp": 1692678480000
}
```

### 4.3 消息类型定义

| 方向 | 类型 | 说明 | 频率限制 |
|------|------|------|----------|
| 上行 | `ping` | 心跳保活 | 每 15s |
| 上行 | `join_room` | 加入房间 | 按需 |
| 上行 | `leave_room` | 离开房间 | 按需 |
| 下行 | `pong` | 心跳应答 | — |
| 下行 | `room_user_list` | 在线列表广播 | 用户进出时 |
| 下行 | `room_status_change` | 房间状态变更 | 开播/关播时 |
| 下行 | `kick_notification` | 踢出通知 | 管理员操作时 |
| 下行 | `reconnect_command` | 重连指令 | 服务重启前 |

### 4.4 心跳消息

```json
// 上行 ping
{
    "type": "ping",
    "msgId": "msg_1692678480000"
}

// 下行 pong
{
    "type": "pong",
    "msgId": "msg_1692678480000",
    "timestamp": 1692678480000
}
```

### 4.5 房间消息

```json
// 加入房间
{
    "type": "join_room",
    "data": {
        "roomId": "room_xxx",
        "uid": "uid_xxx"
    }
}

// 在线列表广播
{
    "type": "room_user_list",
    "data": {
        "roomId": "room_xxx",
        "users": [
            {"uid": "uid_1", "username": "user1", "role": "publisher"},
            {"uid": "uid_2", "username": "user2", "role": "audience"}
        ],
        "onlineCount": 2
    }
}
```

### 4.6 踢出通知

```json
{
    "type": "kick_notification",
    "data": {
        "reason": "管理员强制踢出",
        "operator": "admin_uid"
    },
    "msgId": "msg_1692678480000"
}
```

### 4.7 消息可靠性

- 管理指令（踢人、关播等）需要客户端 ACK 确认
- 超时未 ACK 重试 3 次，每次间隔 2s
- 断线重连后从 Redis 离线消息队列补发未消费消息

```json
// 客户端 ACK
{
    "type": "ack",
    "data": {
        "ackMsgId": "msg_1692678480000"
    }
}
```

## 五、心跳机制实现

### 5.1 心跳时序

```
客户端                              WebSocket 服务
  |                                     |
  |------- ping (每15s) --------------->|
  |                                     |-- 更新 Redis 心跳时间戳
  |<------ pong ------------------------|
  |                                     |
  |      (每10s 扫描)                   |
  |                                     |-- 扫描所有在线用户
  |                                     |-- 检查最后心跳时间
  |                                     |-- 超时 45s 则判定离线
```

### 5.2 心跳超时处理

```java
@Component
public class HeartbeatScanTask {
    @Scheduled(fixedRate = 10000)
    public void scanHeartbeatTimeout() {
        // 1. 获取所有在线用户
        Set<String> onlineUsers = redisTemplate.opsForSet().members("online:users");
        long now = System.currentTimeMillis();

        for (String uid : onlineUsers) {
            String key = "user:" + uid + ":heartbeat";
            String lastHeartbeat = redisTemplate.opsForValue().get(key);
            if (lastHeartbeat == null) continue;

            long lastTime = Long.parseLong(lastHeartbeat);
            if (now - lastTime > 45000) {
                // 2. 判定离线，执行清理
                handleUserOffline(uid);
            }
        }
    }

    private void handleUserOffline(String uid) {
        // 1. 关闭 WebSocket 连接
        // 2. 调用 SRS API 断开媒体流
        // 3. 清理 Redis 在线状态
        // 4. 广播下线通知
    }
}
```

### 5.3 断线重连

```java
// 指数退避策略：1s -> 2s -> 4s -> 8s
private static final int[] RECONNECT_DELAYS = {1000, 2000, 4000, 8000};

// 重连后恢复
public void onReconnect(String uid, String newSessionId) {
    // 1. 更新路由映射
    redisTemplate.opsForValue().set("user:" + uid + ":node", currentNodeId, 60, TimeUnit.SECONDS);
    // 2. 重新加入房间频道
    // 3. 补发离线消息
    List<String> offlineMsgs = redisTemplate.opsForList().range("user:" + uid + ":offline_msgs", 0, -1);
    for (String msg : offlineMsgs) {
        webSocketService.sendMessage(uid, msg);
    }
    // 4. 清理离线消息队列
    redisTemplate.delete("user:" + uid + ":offline_msgs");
}
```

## 六、SRS 集成设计

### 6.1 配置规范

```conf
# SRS 服务器配置
http_api {
    enabled     on;
    listen      1985;
    auth {
        enabled     on;
        username    admin;
        password    ${SRS_API_PASSWORD};
    }
}

rtc_server {
    enabled     on;
    protocol    udp;
    listen      8000;
    candidate   ${SRS_CANDIDATE};
}

http_hooks {
    enabled     on;
    on_publish  http://${BACKEND_HOST}/api/v1/srs/callback/on_publish;
    on_unpublish http://${BACKEND_HOST}/api/v1/srs/callback/on_unpublish;
    on_play     http://${BACKEND_HOST}/api/v1/srs/callback/on_play;
    on_stop     http://${BACKEND_HOST}/api/v1/srs/callback/on_stop;
}

peer_idle_timeout   30;
stun_timeout        20;
hls {
    enabled     off;
}
```

### 6.2 SRS API 调用封装

```java
@Component
public class SrsApiClient {
    private final RestTemplate restTemplate;

    public SrsApiClient(@Value("${srs.api.password}") String password) {
        this.restTemplate = new RestTemplate();
        // 添加 Basic Auth 拦截器
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().setBasicAuth("admin", password);
            return execution.execute(request, body);
        });
    }

    // 踢除连接
    public void deleteClient(String srsHost, String clientId) {
        String url = "http://" + srsHost + ":1985/api/v1/clients/" + clientId;
        restTemplate.delete(url);
    }

    // 查询所有流
    public List<SrsStream> listStreams(String srsHost) {
        String url = "http://" + srsHost + ":1985/api/v1/streams";
        return restTemplate.getForObject(url, SrsStreamList.class).getStreams();
    }

    // 查询所有客户端
    public List<SrsClient> listClients(String srsHost) {
        String url = "http://" + srsHost + ":1985/api/v1/clients";
        return restTemplate.getForObject(url, SrsClientList.class).getClients();
    }
}
```

### 6.3 回调鉴权处理

```java
@Component
public class SrsCallbackService {
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private OnlineUserService onlineUserService;

    public String handleOnPublish(SrsCallbackRequest request) {
        // 从 URL query 中提取 token
        String token = extractToken(request.getQuery());
        try {
            Claims claims = jwtUtil.parseToken(token);
            String uid = claims.get("uid");
            String role = claims.get("role");

            if (!"publisher".equals(role)) {
                return "403 forbidden: only publisher can push stream";
            }

            // 绑定 client_id
            onlineUserService.bindSrsClient(uid, request.getClientId(), request.getSrsNodeId());
            return "200";
        } catch (JwtException e) {
            return "403 forbidden: invalid token";
        }
    }

    public String handleOnPlay(SrsCallbackRequest request) {
        String token = extractToken(request.getQuery());
        try {
            Claims claims = jwtUtil.parseToken(token);
            String uid = claims.get("uid");
            String role = claims.get("role");

            if (!"audience".equals(role) && !"admin".equals(role)) {
                return "403 forbidden: only audience can play stream";
            }

            onlineUserService.bindSrsClient(uid, request.getClientId(), request.getSrsNodeId());
            return "200";
        } catch (JwtException e) {
            return "403 forbidden: invalid token";
        }
    }
}
```

## 七、鉴权与安全设计

### 7.1 JWT 令牌

```java
@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.expiration:7200}")
    private long expiration; // 默认 2 小时

    public String generateToken(String uid, String username, String role) {
        return Jwts.builder()
            .claim("uid", uid)
            .claim("username", username)
            .claim("role", role)
            .setSubject(uid)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + expiration * 1000))
            .signWith(SignatureAlgorithm.HS256, secret)
            .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
            .setSigningKey(secret)
            .parseClaimsJws(token)
            .getBody();
    }
}
```

### 7.2 推拉流 URL 生成

```java
public String generateWhipUrl(String srsHost, String roomId, String uid) {
    String token = jwtUtil.generateToken(uid, username, "publisher");
    return String.format(
        "https://%s:1985/rtc/v1/whip/?token=%s&app=live&stream=%s",
        srsHost, token, roomId
    );
}

public String generateWhepUrl(String srsHost, String roomId, String uid) {
    String token = jwtUtil.generateToken(uid, username, "audience");
    return String.format(
        "https://%s:1985/rtc/v1/whep/?token=%s&app=live&stream=%s",
        srsHost, token, roomId
    );
}
```

### 7.3 WebSocket 鉴权拦截器

```java
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {
    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String query = request.getURI().getQuery();
        String token = extractToken(query);
        try {
            Claims claims = jwtUtil.parseToken(token);
            attributes.put("uid", claims.get("uid"));
            attributes.put("username", claims.get("username"));
            attributes.put("role", claims.get("role"));
            return true;
        } catch (JwtException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }
}
```

## 八、SRS 集群管理

### 8.1 节点注册与健康检查

```java
@Component
public class SrsClusterManager {
    @Autowired
    private RedisTemplate redisTemplate;

    // 节点注册
    public void registerNode(SrsNode node) {
        // 1. 写入 MySQL
        srsNodeMapper.insert(node);
        // 2. 加入 Redis 集群列表
        redisTemplate.opsForZSet().add("srs:cluster:nodes", node.getNodeId(), 0);
        // 3. 设置连接数
        redisTemplate.opsForValue().set("srs:node:" + node.getNodeId() + ":connections", "0");
    }

    // 健康检查
    @Scheduled(fixedRate = 10000)
    public void healthCheck() {
        List<SrsNode> nodes = srsNodeMapper.selectAll();
        for (SrsNode node : nodes) {
            boolean alive = checkNodeHealth(node);
            if (!alive) {
                node.setStatus("inactive");
                srsNodeMapper.updateById(node);
                redisTemplate.opsForZSet().remove("srs:cluster:nodes", node.getNodeId());
                // 记录异常事件
                clusterEventService.recordEvent(node.getNodeId(), "node_down", "critical");
            }
        }
    }

    private boolean checkNodeHealth(SrsNode node) {
        try {
            String url = "http://" + node.getIp() + ":" + node.getApiPort() + "/api/v1/versions";
            restTemplate.getForObject(url, String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

### 8.2 调度策略

```java
@Component
public class SrsScheduler {
    public String selectOptimalNode(String roomId) {
        // 1. 优先选择同房间已有流所在的节点（亲和性调度）
        String existingNode = getRoomAffinityNode(roomId);
        if (existingNode != null) return existingNode;

        // 2. 选择连接数最少的健康节点
        Set<String> nodes = redisTemplate.opsForZSet().range("srs:cluster:nodes", 0, -1);
        String bestNode = null;
        int minConnections = Integer.MAX_VALUE;

        for (String nodeId : nodes) {
            String connStr = redisTemplate.opsForValue().get("srs:node:" + nodeId + ":connections");
            int connections = Integer.parseInt(connStr != null ? connStr : "0");
            if (connections < minConnections) {
                minConnections = connections;
                bestNode = nodeId;
            }
        }
        return bestNode;
    }
}
```

## 九、第三方 API 服务

### 9.1 AK/SK 鉴权

```java
@Component
public class ThirdPartyAuthInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String accessKey = request.getHeader("X-Access-Key");
        String signature = request.getHeader("X-Signature");
        String timestamp = request.getHeader("X-Timestamp");

        // 1. 校验时间戳（防重放，5 分钟内有效）
        if (Math.abs(System.currentTimeMillis() - Long.parseLong(timestamp)) > 300000) {
            throw new AuthException("request expired");
        }

        // 2. 查询 AK 对应的 SK
        String secretKey = thirdPartyAppService.getSecretKey(accessKey);

        // 3. 校验签名
        String expectedSign = hmacSha256(secretKey, timestamp + request.getRequestURI());
        if (!expectedSign.equals(signature)) {
            throw new AuthException("invalid signature");
        }

        return true;
    }
}
```

### 9.2 Webhook 回调

```java
@Component
public class WebhookService {
    public void fireEvent(String eventType, Object payload) {
        List<ThirdPartyApp> subscribers = thirdPartyAppMapper.selectByEvent(eventType);
        for (ThirdPartyApp app : subscribers) {
            String signature = hmacSha256(app.getSecretKey(), eventType + JSON.toJSONString(payload));
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Signature", signature);
            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(payload), headers);

            // 指数退避重试：3s -> 9s -> 27s
            RetryTemplate retry = RetryTemplate.builder()
                .maxAttempts(3)
                .exponentialBackoff(3000, 3)
                .build();
            retry.execute(context -> {
                restTemplate.postForEntity(app.getWebhookUrl(), entity, String.class);
                return null;
            });
        }
    }
}
```

## 十、定时任务

| 任务名 | 调度频率 | 说明 |
|--------|----------|------|
| heartbeatScan | 每 10s | 扫描心跳超时用户 |
| srsHealthCheck | 每 10s | SRS 节点健康检查 |
| staleConnectionCleanup | 每 60s | 清理僵死 WebSocket 连接 |
| offlineDataCleanup | 每 5min | 清理过期离线消息 |
| roomStatusSync | 每 30s | 同步直播间状态 |

## 十一、异常处理

### 11.1 全局异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse> handleAuthException(AuthException e) {
        return ResponseEntity.status(401).body(
            ApiResponse.error(40001, e.getMessage())
        );
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse> handleBusinessException(BusinessException e) {
        return ResponseEntity.status(400).body(
            ApiResponse.error(e.getCode(), e.getMessage())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleException(Exception e) {
        return ResponseEntity.status(500).body(
            ApiResponse.error(50000, "internal server error")
        );
    }
}
```

### 11.2 错误码定义

| 错误码 | 说明 |
|--------|------|
| 40001 | 无效的 JWT 令牌 |
| 40002 | 令牌已过期 |
| 40003 | 无权限操作 |
| 40004 | 房间不存在 |
| 40005 | 用户不在线 |
| 40006 | 无效的签名 |
| 40007 | 请求频率超限 |
| 50001 | SRS API 调用失败 |
| 50002 | 数据库操作失败 |
| 50003 | Redis 操作失败 |

## 十二、性能与容量规划

### 12.1 单节点容量

| 指标 | 目标值 | 说明 |
|------|--------|------|
| WebSocket 连接数 | 50000 | 单节点支持 |
| 直播间数 | 1000 | 单节点支撑 |
| 消息吞吐量 | 10000 msg/s | 单节点处理能力 |
| 心跳延迟 | < 50ms | P99 响应时间 |
| 广播延迟 | < 100ms | 单房间 1000 人广播 |

### 12.2 数据库连接池

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 5000
```

### 12.3 Redis 连接池

```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 32
        max-idle: 8
        min-idle: 4
        max-wait: 3000ms
```

## 十三、部署与运维

### 13.1 容器化部署

```yaml
# docker-compose.yml
version: '3.8'
services:
  srs:
    image: ossrs/srs:6.0
    ports:
      - "1935:1935"   # RTMP
      - "1985:1985"   # HTTP API
      - "8000:8000/udp" # WebRTC UDP
    environment:
      - SRS_CANDIDATE=${SRS_CANDIDATE}
      - BACKEND_HOST=${BACKEND_HOST}
    volumes:
      - ./srs.conf:/usr/local/srs/conf/srs.conf

  backend:
    build: .
    ports:
      - "8080:8080"
    environment:
      - NODE_ID=ws-node-1
      - MYSQL_HOST=${MYSQL_HOST}
      - MYSQL_USER=${MYSQL_USER}
      - MYSQL_PASSWORD=${MYSQL_PASSWORD}
      - REDIS_HOST=${REDIS_HOST}
      - REDIS_PASSWORD=${REDIS_PASSWORD}
      - JWT_SECRET=${JWT_SECRET}
      - SRS_API_PASSWORD=${SRS_API_PASSWORD}
      - SRS_HOST=${SRS_HOST}
      - SRS_CANDIDATE=${SRS_CANDIDATE}
      - BACKEND_HOST=${BACKEND_HOST}
    depends_on:
      - mysql
      - redis
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3

  mysql:
    image: mysql:8.0
    ports:
      - "3306:3306"
    environment:
      - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
      - MYSQL_DATABASE=srs_live
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7.0
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  mysql-data:

networks:
  default:
    driver: bridge
```

### 13.2 环境变量清单

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `NODE_ID` | 当前节点ID | ws-node-1 |
| `MYSQL_HOST` | MySQL 地址 | localhost |
| `MYSQL_USER` | MySQL 用户 | root |
| `MYSQL_PASSWORD` | MySQL 密码 | — |
| `REDIS_HOST` | Redis 地址 | localhost |
| `REDIS_PASSWORD` | Redis 密码 | — |
| `JWT_SECRET` | JWT 签名密钥 | — |
| `SRS_API_PASSWORD` | SRS API 密码 | — |
| `SRS_HOST` | SRS 地址 | localhost |
| `SRS_CANDIDATE` | SRS ICE candidate | — |
| `BACKEND_HOST` | 后端回调地址 | localhost:8080 |

---

## 十四、视频流畅与稳定建议与实践

### 14.1 WebRTC 推拉流参数优化

#### 14.1.1 编码器推荐配置

```javascript
// 前端推流 MediaStream 约束
const constraints = {
    video: {
        width: { ideal: 1280, max: 1920 },
        height: { ideal: 720, max: 1080 },
        frameRate: { ideal: 30, max: 30 },
        bitrate: 2500000,
        codec: "H264"
    },
    audio: {
        sampleRate: 48000,
        channelCount: 1,
        bitrate: 64000
    }
};
```

**推荐编码参数速查表：**

| 场景 | 分辨率 | 帧率 | 视频码率 | 音频码率 | 适用网络 |
|------|--------|------|----------|----------|----------|
| 高清直播 | 1080p | 30fps | 3-4 Mbps | 64 Kbps | 带宽 >= 5 Mbps |
| 标准直播 | 720p | 30fps | 1.5-2.5 Mbps | 64 Kbps | 带宽 >= 3 Mbps |
| 流畅直播 | 540p | 24fps | 0.8-1.2 Mbps | 48 Kbps | 带宽 >= 1.5 Mbps |
| 低带宽适配 | 360p | 20fps | 0.4-0.6 Mbps | 32 Kbps | 带宽 >= 0.8 Mbps |

#### 14.1.2 Simulcast 分层编码

主播端开启 Simulcast 推流，SRS 同时分发多个质量层，观众端根据自身网络条件动态选择：

```javascript
const pc = new RTCPeerConnection(config);
const sender = pc.addTrack(videoTrack, stream);
const parameters = sender.getParameters();
parameters.encodings = [
    { rid: "h", maxBitrate: 3000000 },
    { rid: "m", maxBitrate: 1500000 },
    { rid: "l", maxBitrate: 500000 }
];
sender.setParameters(parameters);
```

观众端接收策略：

| 网络 RTT | 丢包率 | 选择层级 | 降级策略 |
|----------|--------|----------|----------|
| < 100ms | < 1% | 高清层 (h) | 平稳播放 |
| 100-200ms | 1-3% | 中清层 (m) | 降一级 |
| 200-400ms | 3-5% | 低清层 (l) | 降两级 |
| > 400ms | > 5% | 仅音频或暂停 | 极端降级 |

### 14.2 网络自适应策略

#### 14.2.1 带宽探测与码率调整

```javascript
class BandwidthEstimator {
    constructor() {
        this.currentBitrate = 2500000;
        this.minBitrate = 200000;
        this.maxBitrate = 5000000;
        this.packetLossThreshold = 0.05;
        this.rttThreshold = 300;
        this.probeInterval = 3000;
    }
    updateMetrics(packetLoss, rtt, receivedBitrate) {
        if (packetLoss > this.packetLossThreshold || rtt > this.rttThreshold) {
            this.currentBitrate = Math.max(this.currentBitrate * 0.8, this.minBitrate);
        } else if (packetLoss < 0.01 && rtt < 100) {
            this.currentBitrate = Math.min(this.currentBitrate * 1.1, this.maxBitrate);
        }
        return this.currentBitrate;
    }
}
```

#### 14.2.2 前端自适应切换

```java
public String recommendLayer(String uid, String roomId) {
    String key = "qos:" + uid + ":" + roomId;
    QosMetrics metrics = redisTemplate.opsForValue().get(key);
    if (metrics == null) return "m";
    if (metrics.getPacketLoss() > 0.05 || metrics.getRtt() > 400) return "l";
    else if (metrics.getPacketLoss() > 0.03 || metrics.getRtt() > 200) return "m";
    else return "h";
}
```

### 14.3 首帧秒开优化

#### 14.3.1 播放器预加载

```javascript
async function fastStart() {
    const pc = new RTCPeerConnection({
        iceServers: [{ urls: "stun:stun.live.com:3478" }]
    });
    pc.addTransceiver("audio", { direction: "recvonly" });
    pc.addTransceiver("video", { direction: "recvonly" });
    const response = await fetch(whepUrl, {
        method: "POST",
        headers: { "Content-Type": "application/sdp" },
        body: btoa(JSON.stringify(pc.localDescription.sdp))
    });
    const sdp = await response.text();
    await pc.setRemoteDescription({ type: "answer", sdp: atob(sdp) });
}
```

#### 14.3.2 首帧时间优化目标

| 阶段 | 目标时间 | 优化手段 |
|------|----------|----------|
| DNS 解析 | < 50ms | 预解析、CDN 加速 |
| ICE 连接 | < 200ms | STUN 优先、ICE 候选预采集 |
| SDP 协商 | < 100ms | 服务端预生成 SDP、减少协商轮次 |
| 首帧解码 | < 300ms | H264 硬解码、预分配解码器 |
| 首帧渲染 | < 100ms | 优先渲染关键帧 |
| **总耗时** | **< 750ms** | 全链路优化 |

### 14.4 抗丢包与 FEC 策略

**FEC 冗余度推荐：**

| 丢包率 | FEC 冗余度 | 带宽开销 | 说明 |
|--------|------------|----------|------|
| < 1% | 0% (不启用) | 0% | 网络良好，无需冗余 |
| 1-3% | 10% | +10% | 轻度丢包，低冗余保护 |
| 3-5% | 20% | +20% | 中度丢包，中等冗余 |
| 5-10% | 30% | +30% | 重度丢包，高冗余保护 |
| > 10% | 40% + NACK | +40% | 极端丢包，组合FEC+NACK |

#### NACK 重传策略

```java
public class NackConfig {
    public static final int MAX_RETRANSMITS = 3;
    public static final int RETRANSMIT_TIMEOUT = 50;
    public static final int NACK_QUEUE_SIZE = 200;
    public static final int PLI_INTERVAL = 1000;
}
```

### 14.5 端到端延迟控制

#### 14.5.1 延迟等级配置

| 模式 | 目标延迟 | 适用场景 | 配置 |
|------|----------|----------|------|
| 超低延迟 | < 500ms | 互动连麦、在线教育 | 关闭 JitterBuffer、最小化缓冲区 |
| 低延迟 | 500ms - 1s | 常规直播、电商带货 | 自适应 JitterBuffer、动态调整 |
| 普通延迟 | 1s - 3s | 娱乐直播、体育赛事 | 标准 JitterBuffer、抗抖动优先 |

#### 14.5.2 SRS 端延迟优化

```conf
rtc_server {
    nack_no_copy    on;
    min_send_buffer 100;
    twcc_enabled    on;
    remb_enabled    off;
}
```

### 14.6 服务端 QoS 保障

#### 14.6.1 实时 QoS 数据采集

```java
public class QosMetrics {
    private String uid;
    private String roomId;
    private double packetLoss;
    private int rtt;
    private int jitter;
    private int receivedBitrate;
    private int frameRate;
    private int resolutionWidth;
    private int resolutionHeight;
    private double freezeRate;
    private long timestamp;
}
```

QoS 上报接口：

```
POST /api/v1/qos/report
{"uid":"uid_xxx","roomId":"room_xxx","packetLoss":0.02,"rtt":120,"freezeRate":0.001}
```

#### 14.6.2 服务质量监控

| 指标 | 优秀 | 良好 | 一般 | 差 |
|------|------|------|------|-----|
| 丢包率 | < 0.5% | 0.5-2% | 2-5% | > 5% |
| RTT | < 50ms | 50-150ms | 150-300ms | > 300ms |
| 卡顿率 | < 0.1% | 0.1-0.5% | 0.5-2% | > 2% |
| 帧率达标率 | > 95% | 85-95% | 70-85% | < 70% |
| 首帧时间 | < 500ms | 500ms-1s | 1-2s | > 2s |

### 14.7 异常场景处理

| 问题 | 现象 | 原因 | 解决方案 |
|------|------|------|----------|
| 画面卡顿 | 帧率骤降、画面冻结 | 上行带宽不足 | 降码率、切 Simulcast 低层、关闭摄像头 |
| 音画不同步 | 声音超前/滞后画面 | 时间戳漂移、缓冲区溢出 | 校准 NTP 时间、重置 RTP 时间戳基线 |
| 黑屏无画面 | 播放器无视频渲染 | ICE 连接失败、SDP 协商异常 | 重连 WebRTC、回退到 RTMP 播放 |
| 频繁断流 | 播放器反复缓冲 | 网络抖动、ICE 连接断裂 | 开启 ICE Restart、增加重连缓冲 |
| 高延迟 | 画面延迟超过 5s | JitterBuffer 过大、网络拥塞 | 动态缩减缓冲区、启用 TWCC 拥塞控制 |

### 14.8 服务端质量监控看板

| 指标 | 采集方式 | 刷新频率 | 展示方式 |
|------|----------|----------|----------|
| 推流成功率 | on_publish 回调统计 | 1min | 折线图 |
| 拉流成功率 | on_play 回调统计 | 1min | 折线图 |
| 人均卡顿率 | QoS 上报聚合 | 10s | 数值 + 趋势 |
| 平均首帧时间 | 客户端上报 | 1min | 折线图 |
| 平均 RTT | QoS 上报聚合 | 10s | 数值 + 热力图 |

**质量告警规则：**

| 告警项 | 触发条件 | 严重程度 | 通知方式 |
|--------|----------|----------|----------|
| 推流成功率下降 | 5min 内成功率 < 90% | Critical | 电话 + 短信 |
| 拉流成功率下降 | 5min 内成功率 < 85% | Critical | 电话 + 短信 |
| 平均卡顿率超标 | 5min 内卡顿率 > 5% | Warning | 企业微信 |
| 平均首帧时间超时 | 5min 内首帧 > 3s | Warning | 企业微信 |
| 节点异常 | 连续 3 次健康检查失败 | Critical | 电话 + 短信 |