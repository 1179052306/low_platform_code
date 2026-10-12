package com.api.lowcode.engine;

/**
 * Snowflake ID 生成器（64 bit long）
 *
 * | 1bit 符号(0) | 41bit 时间戳(ms) | 10bit workerId | 12bit 序列号 |
 *
 * 同一毫秒同一 worker 可生成 4096 个 ID，全局唯一、递增、可排序。
 * workerId 由 SnowflakeWorkerIdAssigner 在启动时设置。
 */
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1704067200000L; // 2024-01-01 00:00:00 UTC
    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1; // 1023
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;   // 4095
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private static volatile long workerId = 0L;
    private static long sequence = 0L;
    private static long lastTimestamp = -1L;

    public static void setWorkerId(long wid) {
        if (wid < 0 || wid > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId 超出范围 [0," + MAX_WORKER_ID + "]: " + wid);
        }
        workerId = wid;
    }

    public static long getWorkerId() {
        return workerId;
    }

    public static synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException(
                "时钟回拨: current=" + timestamp + ", last=" + lastTimestamp);
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private static long tilNextMillis(long lastTs) {
        long ts = System.currentTimeMillis();
        while (ts <= lastTs) {
            ts = System.currentTimeMillis();
        }
        return ts;
    }
}