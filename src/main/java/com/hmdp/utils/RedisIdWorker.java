package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author MoFany-J
 * @date 2023/3/9
 * @description RedisIdWorker 基于redis自增的id生成策略实现
 */
@Component
public class RedisIdWorker {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    /**
     * 开始的时间戳，秒数，2023-02-20
     */
    private static final long BEGIN_TIMESTAMP = 1676822400L;

    /**
     * 序列号位数
     */
    private static final int COUNT_BITS = 32;

    /**
     * 返回id
     *
     * @param keyPrefix 不同业务的key前缀
     */
    public long nextId(String keyPrefix) {
        LocalDateTime nowDateTime = LocalDateTime.now();
        long currentTimestamp = nowDateTime.toEpochSecond(ZoneOffset.UTC);
        // 生成时间戳
        long timestamp = currentTimestamp - BEGIN_TIMESTAMP;
        // 生成序列号，日期以冒号为分隔符，这样存储在Redis中便于按年月日分别统计
        String nowDate = nowDateTime.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        String key = "icr:" + keyPrefix + ":" + nowDate;
        // 自增长key，单个key自增长上限为：2^6
        Long increment = stringRedisTemplate.opsForValue().increment(key);
        // long类型拼接并返回
        long GloballyUniqueId = timestamp << COUNT_BITS | increment;
        // 全局唯一id格式：符号位1bit + 时间戳31bit + 序列号32bit
        return GloballyUniqueId;
    }
}
