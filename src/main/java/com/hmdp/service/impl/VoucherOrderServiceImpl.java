package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    /**
     * 秒杀卷库存业务逻辑
     */
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    /**
     * 注入id生成器
     */
    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 判断秒杀是否开始，开始时间在当前时间之后
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始!");
        }
        // 判断秒杀是否结束，开始时间在当前时间之前
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束!");
        }
        // 判断库存是否充足，库存小于1
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足!");
        }

        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        /**
         * @implNote 同一个用户加同一把锁，不同用户加不同锁
         * */
        synchronized (userId.toString().intern()) {
            // 防止事务失效，获取当前代理类接口的代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            // 必须确保先提交事务最后释放锁，所以才锁定用户的id字符串常量池返回的值
            return proxy.createVoucherOrder(voucherId);
        }
    }

    /**
     * 一人一单，我们要确保先提交事务最后释放锁
     */
    @Override
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 判断下单用户是否已经存在
        if (count > 0) {
            return Result.fail("当前用户已下过一次单!");
        }
        /**
         * @implNote 扣减库存，利用了乐观锁CAS原理，比较并交换
         * 使用了修改SQL的方法，来达到CAS乐观锁原理
         * */
        boolean success = seckillVoucherService.update()
                // set stock = stock - 1
                .setSql("stock = stock - 1")
                // where id = ? and stock > 0
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        // 扣减库存失败
        if (!success) {
            return Result.fail("库存不足!");
        }
        // 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 生成订单id
        long orderId = redisIdWorker.nextId("order");
        // 给订单设置订单id
        voucherOrder.setId(orderId);
        // 给订单设置用户id
        voucherOrder.setUserId(userId);
        // 给订单设置优惠卷id
        voucherOrder.setVoucherId(voucherId);
        // 将订单写入数据库
        save(voucherOrder);
        // 返回订单id
        return Result.ok(orderId);
    }
}
