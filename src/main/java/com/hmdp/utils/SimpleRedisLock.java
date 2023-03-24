package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
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
     *
     * @param name                锁名
     * @param stringRedisTemplate redis操作
     */
    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";

    /**
     * 线程的唯一锁标识
     */
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    /**
     * 加载Lua脚本
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        // 类首次加载时初始化
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        // 指定脚本位置
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("script/unlock.lua"));
        // 指定返回值类型
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    /**
     * 尝试获取锁
     *
     * @param timeoutSec 锁持有的超时时间，过期自动释放
     * @return true代表获取锁成功，false代表获取锁失败
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        String key = KEY_PREFIX + name;
        // 获取当前线程标识，即：value
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, threadId, timeoutSec, TimeUnit.SECONDS);
        // 防止自动拆箱时发生空指针异常
        return Boolean.TRUE.equals(result);
    }

    /**
     * 锁释放
     */
    @Override
    public void unlock() {
        String key = KEY_PREFIX + name;
        // 获取当前线程标识，即：value
        String value = ID_PREFIX + Thread.currentThread().getId();
        // 调用Lua脚本进行锁释放
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), value);
    }
}
