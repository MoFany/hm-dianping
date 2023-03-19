package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @author MoFany-J
 * @date 2023/3/15
 * @description SimpleRedisLock 简单的Redis锁
 */
public class SimpleRedisLock implements ILock {
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 锁对象构造器
     * @param name 锁名
     * @param stringRedisTemplate redis操作
     * */
    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";

    /**
     * 尝试获取锁
     *
     * @param timeoutSec 锁持有的超时时间，过期自动释放
     * @return true代表获取锁成功，false代表获取锁失败
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        String key = KEY_PREFIX + name;
        // 获取当前线程标识
        long threadId = Thread.currentThread().getId();
        String value = threadId + "";
        // 获取锁
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, value, timeoutSec, TimeUnit.SECONDS);
        // 防止自动拆箱时发生空指针异常
        return Boolean.TRUE.equals(result);
    }

    /**
     * 锁释放
     */
    @Override
    public void unlock() {
        String key = KEY_PREFIX + name;
        // 释放锁
        stringRedisTemplate.delete(key);
    }
}
