package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import io.lettuce.core.RedisClient;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.*;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    /**
     * 按id查询
     *
     * @param id
     */
    @Override
    public Result queryById(Long id) {

        // 自定义缓存穿透实现
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);

        // 逻辑过期解决缓存击穿
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺不存在!");
        }
        // 返回
        return Result.ok(shop);
    }

    /**
     * 互斥锁解决缓存穿透
     */
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断缓存中是否存在，空白内容校验
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.缓存命中，则直接返回
            log.debug("缓存命中!有效值！");
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 命中的是否是空值
        if (shopJson != null) {
            // 返回错误信息
            log.debug("缓存命中!无效值！");
            return null;
        }
        /**
         * 未命中开始实现缓存重建，获取互斥锁
         * */
        log.debug("缓存未命中!");
        String lockKey = "lock:shop:" + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2判断是否获取成功
            if (!isLock) {
                // 4.3 失败，则休眠并重试
                Thread.sleep(50);
                // 睡醒之后重新获取锁
                return queryWithMutex(id);
            }
            // 4.4获取成功，根据id查询数据库
            shop = getById(id);
            // 休眠，模拟重建缓存时的延时
            Thread.sleep(200);
            // 将空值写入 Redis 缓存
            if (shop == null) {
                // 缓存空对象，到redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 返回错误信息
                return null;
            }
            // 6.存在，写缓存
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // 7.释放互斥锁
            unLock(lockKey);
        }
        // 8.返回
        return shop;
    }

    /**
     * 添加锁
     */
    private boolean tryLock(String key) {
        log.debug("获取锁！");
        String value = "1";
        int expire = 10;
        // 执行Redis中的setnx操作
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, value, expire, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(result);
    }

    /**
     * 释放锁
     */
    private void unLock(String key) {
        log.debug("删除锁！");
        stringRedisTemplate.delete(key);
    }

    /**
     * 更新
     *
     * @param shop
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        // 校验id
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空!");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
