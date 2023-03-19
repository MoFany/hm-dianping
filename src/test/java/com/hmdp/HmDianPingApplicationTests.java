package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * 单体SpringBootApp
 * 后端数据访问接口：http://localhost:8081/shop-type/list
 * 前端数据访问接口：localhost:8080
 */
@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private CacheClient cacheClient;

    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);

    }


    /**
     * 简单的线程池
     */
    private ExecutorService THREAD_EXECUTOR = Executors.newFixedThreadPool(500);

    @Resource
    private RedisIdWorker redisIdWorker;

    /**
     * 全局唯一id单元测试
     */
    @Test
    public void test() throws InterruptedException {
        // 使一个线程等待其他线程完成各自的工作后再执行
        CountDownLatch latch = new CountDownLatch(300);
        // 线程要做的任务
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                // 同一秒内生成的id前缀一致
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            // 每一个线程执行完，就倒计时直到为0为止
            latch.countDown();
        };
        // 开启计时
        long beginTime = System.currentTimeMillis();
        // 提交任务
        for (int i = 0; i < 300; i++) {
            THREAD_EXECUTOR.submit(task);
        }
        // 等待所有线程完成
        latch.await();
        // 所有线程执行完成后计时
        long endTime = System.currentTimeMillis();
        System.out.println("time = " + (endTime - beginTime));
    }
}
