package com.hmdp.service.impl;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询类型列表
     */
    @Override
    public Result queryTypeList() {
        // 1.先从redis缓存中查询
        String key = CACHE_SHOP_KEY + "type:list";
        List<String> typeListStr = stringRedisTemplate.opsForList().range(key, 0, 1);
        // 2.判断redis缓存中的数据，存在则直接返回
        if (typeListStr != null && typeListStr.size() != 0) {
            log.debug("缓存命中!!!");
            List<ShopType> shopTypes = JSONUtil.toList(typeListStr.get(0), ShopType.class);
            return Result.ok(shopTypes);
        }
        log.debug("缓存未命中!!!");
        // 3.redis缓存中不存在时，从数据库中查询
        List<ShopType> typeList = query().orderByAsc("sort").list();
        // 4.判断数据库中查询到的内容是否为空
        if (typeList == null || typeList.size() == 0) {
            return Result.fail("类型列表为空!");
        }
        // 5.不为空，则先写入缓存
        stringRedisTemplate.opsForList().rightPushAll(key, JSONUtil.toJsonStr(typeList));
        // 6.返回结果
        return Result.ok(typeList);
    }
}
