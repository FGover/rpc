package com.fg;

import com.fg.utils.DateUtil;

/**
 * 请求id生成器
 * 雪花算法
 * 符号位        时间戳               机器id            序列号
 * 0  | 41-bit timestamp | 10-bit machine id | 12-bit sequence
 */
public class IdGenerator {

    // 起始时间戳
    public static final long START_TIME_STAMP = DateUtil.get("2025-6-18").getTime();
    // 机器id
    private static final long MACHINE_ID_BITS = 5L;
    // 数据中心id
    private static final long DATACENTER_ID_BITS = 5L;
    // 序列号位数
    private static final long SEQUENCE_BITS = 12L;
    // 机器id最大值
    private static final long MAX_MACHINE_ID = ~(-1L << MACHINE_ID_BITS);
    // 数据中心id最大值
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    // 机器id左移位数
    private static final long MACHINE_ID_LEFT_SHIFT = SEQUENCE_BITS;
    // 数据中心id左移位数
    private static final long DATACENTER_ID_LEFT_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;
    // 时间戳左移位数
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS + DATACENTER_ID_BITS;
    // 序列号掩码，防止溢出
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    // 数据id（0~31）
    private long datacenterId;
    // 机器id（0~31）
    private long machineId;
    // 序列号
    private long sequence = 0L;
    // 上次生成id的时间戳
    private long lastTimestamp = -1L;

    public IdGenerator(long datacenterId, long machineId) {
        if (datacenterId > DATACENTER_ID_BITS || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("datacenterId必须在0和%d之间", MAX_DATACENTER_ID));
        }
        if (machineId > MACHINE_ID_BITS || machineId < 0) {
            throw new IllegalArgumentException(String.format("machineId必须在0和%d之间", MAX_MACHINE_ID));
        }
        this.datacenterId = datacenterId;
        this.machineId = machineId;
    }

    /**
     * 生成唯一id
     *
     * @return
     */
    public synchronized long nextId() {
        long timestamp = currentTime();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("系统时钟回退，拒绝生成ID");
        }
        if (lastTimestamp == timestamp) {
            // 同一毫秒内生成的ID，序列号加1
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 如果当前毫秒的序列号满了(4095)，则等待下一毫秒
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            // 不同毫秒，重置序列号
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - START_TIME_STAMP) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_LEFT_SHIFT)
                | (machineId << MACHINE_ID_LEFT_SHIFT)
                | sequence;
    }

    /**
     * 等待下一毫秒
     *
     * @param lastTimestamp
     * @return
     */
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTime();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTime();
        }
        return timestamp;
    }

    /**
     * 获取当前时间戳
     *
     * @return
     */
    public long currentTime() {
        return System.currentTimeMillis();
    }

}
