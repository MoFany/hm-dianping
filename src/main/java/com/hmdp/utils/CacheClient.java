package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * @author MoFany-J
 * @date 2023/3/9
 * @description CacheClient 缓存问题解决工具类
 */
@Slf4j
@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 写redis
     *
     * @param key   键
     * @param value 值
     * @param time  时间
     * @param unit  单位
     */
    public void set(String key, Object value, long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 逻辑过期
     *
     * @param key   键
     * @param value 值
     * @param time  时间
     * @param unit  单位
     */
    public void setWithLogicalExpire(String key, Object value, long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 缓存空对象解决缓存穿透
     *
     * @param <R>             返回值泛型
     * @param <ID>            数据库查询参数泛型
     * @param keyPrefix       key前缀
     * @param id              数据库查询参数
     * @param clazz           返回值类型
     * @param dbQueryFunction 数据库查询功能逻辑（函数式编程）
     * @param time            时间
     * @param unit            单位
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> clazz,
            Function<ID, R> dbQueryFunction, long time, TimeUnit unit) {

        // 从redis中查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 判断缓存中是否存在，非空值判断
        if (StrUtil.isNotBlank(json)) {
            // 缓存命中，则直接返回
            log.debug("缓存命中!有效值！");
            return JSONUtil.toBean(json, clazz);
        }
        // 命中的是否是空值
        if (json != null) {
            // 返回错误信息
            log.debug("缓存命中!无效值！");
            return null;
        }
        // 不存在，根据id查询数据库
        log.debug("缓存未命中!");
        // TODO 注意是实现数据库的操作
        R result = dbQueryFunction.apply(id);
        /**
         * 将空值写入 Redis 缓存
         * */
        if (result == null) {
            // 缓存空对象
            stringRedisTemplate.opsForValue().set(key, "", time, unit);
            return null;
        }
        // 存在，写缓存
        this.set(key, result, time, unit);
        // 返回
        return result;
    }

    /**
     * 逻辑过期解决缓存击穿
     *
     * @param <R>             返回类型的泛型
     * @param <ID>            id泛型
     * @param keyPrefix       key前缀
     * @param id              id 要查询的依赖
     * @param clazz           传入的类型
     * @param dbQueryFunction 数据库查询功能逻辑（函数式编程）
     * @param time            时间
     * @param unit            单位
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> clazz,
            Function<ID, R> dbQueryFunction, long time, TimeUnit unit) {

        String key = keyPrefix + id;
        // 从redis中获取缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断缓存不存在返回空
        if (StrUtil.isBlank(shopJson)) {
            log.debug("缓存未命中!");
            return null;
        }
        // 命中，需要先将Json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        // 获取json中的实体数据，并反序列化为对象
        R result = JSONUtil.toBean((JSONObject) redisData.getData(), clazz);
        // 获取json中的逻辑过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断缓存数据是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 过期时间如果在当前时间之后，未过期，直接返回信息
            log.debug("缓存命中!未过期!" + expireTime);
            return result;
        }
        /**
         * 逻辑时间已过期开始实现缓存重建，获取互斥锁
         * */
        log.debug("缓存命中!已过期!");
        String lockKey = "local:key:" + id;
        // 获取锁，redis的setnx语法
        boolean isLock = tryLock(lockKey);
        // 判断是否成功获取锁
        if (isLock) {
            // 将要执行的任务交给线程池中的子线程执行
            getThreadsExecutor().submit(() -> {
                try {
                    // 查数据库
                    R value = dbQueryFunction.apply(id);
                    // 写redis
                    log.debug("重建缓存!");
                    // 缓存重建，即更新当前热点key
                    this.setWithLogicalExpire(key, value, time, unit);
                } finally {
                    // 释放锁
                    unLock(lockKey);
                }
            });
        }
        // 失败，直接返回
        return result;
    }

    /**
     * 创建线程池
     */
    private static ExecutorService getThreadsExecutor() {
        int threads = 10;
        // 线程池7大核心参数
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
                // 线程池常驻线程数
                threads,
                // 线程池最大线程数
                threads << 1,
                // 空闲线程存活时间
                500,
                // 时间单位：毫秒
                TimeUnit.MILLISECONDS,
                // 线程阻塞队列，链表
                new LinkedBlockingQueue<>(),
                // 自定义线程工厂
                runnable -> new Thread(runnable),
                // 线程拒绝策略，调用者线程帮忙执行
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        return threadPool;
    }

    /**
     * 获取锁
     *
     * @param key 加锁的key
     */
    private boolean tryLock(String key) {
        // 执行Redis中的setnx操作，过期时间为10秒，当发生异常该锁未能释放时，根据有效期自动释放
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, "default Lock!", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(result);
    }

    /**
     * 释放锁
     *
     * @param key 释放锁的key
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
