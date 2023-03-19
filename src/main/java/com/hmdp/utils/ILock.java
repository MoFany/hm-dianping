package com.hmdp.utils;

/**
 * @author MoFany-J
 * @date 2023/3/15
 * @description ILock 分布式锁获取接口
 */
public interface ILock {
    /**
     * 尝试获取锁
     *
     * @param timeoutSec 锁持有的超时时间，过期自动释放
     * @return true代表获取锁成功，false代表获取锁失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 锁释放
     */
    void unlock();
}
