## 启动顺序（严格）

```bash
# 0. 创建共享网络
docker network create srs-shared

# 1. 构建 proxy 镜像
docker build --no-cache -t local/srs-proxy:v7 -f Dockerfile.proxy .

# 2. 先启 proxy
docker-compose -f docker-compose.proxy.unified.yml up -d
sleep 5

# 3. 再启 origin（--force-recreate 确保 configs 变更生效）
docker-compose -f docker-compose.srs.unified.yml up -d --force-recreate
sleep 5

# 4. 手动注册 origin 到 proxy（每次 proxy 重启后执行）
curl -s -X POST http://localhost:12025/api/v1/srs/register \
  -H 'Content-Type: application/json' \
  -d '{"server":"srs-origin-1","service":"srs","pid":"1","ip":"srs-origin-1","rtmp":["1935"],"rtc":["8000"],"http":["1986"],"api":["1986"]}'

curl -s -X POST http://localhost:12025/api/v1/srs/register \
  -H 'Content-Type: application/json' \
  -d '{"server":"srs-origin-2","service":"srs","pid":"1","ip":"srs-origin-2","rtmp":["1935"],"rtc":["8000"],"http":["1986"],"api":["1986"]}'

# 5. 启动后端
cd backend && mvn spring-boot:run
```

---

## 端口映射

| 服务 | 宿主机 | 容器内 | 协议 |
|------|--------|--------|------|
| **srs-proxy** | | | |
| RTMP | 1935 | 1935 | tcp |
| HTTP API (WHIP/WHEP) | 1985 | 1985 | tcp |
| HTTP Server (HLS/FLV) | 8081 | 8080 | tcp |
| WebRTC | 8000 | 8000 | tcp+udp |
| 集群管理 (register) | 12025 | 12025 | tcp |
| **srs-origin-1 (直播)** | | | |
| RTMP | 11935 | 1935 | tcp |
| HTTP API | 11986 | 1986 | tcp |
| HTTP Server | 18080 | 8080 | tcp |
| WebRTC | 18000 | 8000 | udp |
| **srs-origin-2 (录制)** | | | |
| RTMP | 12935 | 1935 | tcp |
| HTTP API | 12986 | 1986 | tcp |
| HTTP Server | 19080 | 8080 | tcp |
| WebRTC | 19000 | 8000 | udp |

---

## 阿里云安全组放行端口

```
TCP: 1935, 1985, 8000, 8081, 12025
UDP: 8000
```

---

## 关键配置文件说明

| 文件 | 作用 | 注意 |
|------|------|------|
| `conf/origin1.conf` | 直播节点（DVR off） | candidate 指向公网 IP |
| `conf/origin2.conf` | 录制节点（DVR on） | candidate 指向公网 IP |
| `conf/proxy.srs.conf` | srs-proxy 内置配置 | 静态 origin `host:rtmp:rtc` |
| `conf/proxy.conf` | ossrs/srs 代理模式配置 | cluster mode proxy + 静态 origin |
| `Dockerfile.proxy` | 构建 srs-proxy 镜像 | `--branch v7.0.157` 匹配 origin 版本 |

---

## 后端关键配置

`application.yml` 中 unified 模式端口：
```yaml
unified-proxy-host: 8.137.165.18
unified-proxy-port: 1985     # WHIP/WHEP 信令端口，不是 8000！
```

WHIP/WHEP URL 已改为 `http://`（不是 `https://`）。

---

## 踩坑记录

| 问题 | 原因 | 解决 |
|------|------|------|
| conf 加 env var 后 parse 失败 | SRS v7.0.157 不支持 `${VAR}` 也不支持 `$VAR` | 全部硬编码 |
| origin 加 cluster 块后重启循环 | ossrs/srs:v7.0.157 和 proxy v7.0.160 协议不兼容 | 去掉 cluster，改手动注册 |
| proxy `memory` 模式静态 origin 不生效 | memory 模式也需通过 12025 register API 注册后端 | 手动 POST 注册 |
| 8000 端口 WHIP 超时 | WHIP 信令在 1985，8000 是 WebRTC 媒体端口 | 改 unified-proxy-port 为 1985 |
| proxy `no server available` | Redis 空 + 静态 origin 对 WebRTC 不生效 | 手动注册，http 填 1986（API端口） |
| proxy 500 → origin RTC disabled | vhost 没配 `rtc` 块 | 加 `rtc { enabled on; }` |
| proxy 1102 auth 失败 | http_hooks 回调 172.16.6.116:8180 不通 | 去除 http_hooks |
| configs: 改了不生效 | compose configs 不自动感知文件变更 | `--force-recreate` |

---

## 后端 ClusterService 改动

`selectOptimalNode()`：Redis 无数据时返回 `nodeType` 兜底（`"live"` / `"record"`）。

`generateWhipUrl/generateWhepUrl`：`https://` → `http://`。

`checkNodeHealth()`：节点不在 DB 时不抛异常，返回 `status: unknown`。