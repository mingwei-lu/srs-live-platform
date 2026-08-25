package com.srs.live.common.util;

import org.springframework.stereotype.Component;

/**
 * 雪花算法 ID 生成器
 * 1位符号位 + 41位时间戳 + 10位工作节点ID + 12位序列号
 */
@Component
public class SnowflakeIdGenerator {

    private final long workerId;
    private final long datacenterId = 0L;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    /** 起始时间戳：2024-01-01 00:00:00 */
    private static final long EPOCH = 1704067200000L;

    public SnowflakeIdGenerator() {
        // 从环境变量或配置获取 workerId，默认使用主机名hash
        String nodeId = System.getenv("NODE_ID");
        if (nodeId != null) {
            this.workerId = Math.abs(nodeId.hashCode()) % (int) MAX_WORKER_ID;
        } else {
            this.workerId = Math.abs(System.getProperty("user.name", "0").hashCode()) % (int) MAX_WORKER_ID;
        }
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards, refusing to generate id");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}