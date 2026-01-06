package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    ISeckillVoucherService seckillVoucherService;
    @Resource
    RedisIdWorker redisIdWorker;
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Resource
    RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    //阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){
                //获取队列中的订单信息
                try {
                    //获取订单
                    VoucherOrder voucherOrder = orderTasks.take();
                    //异步处理
                    handleVoucher(voucherOrder);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }


    }
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private  IVoucherOrderService proxy;

    public Result seckillVoucher(Long voucherId){
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //执行lua脚本
        Long res = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId,
                userId
        );
        //判断返回结果
        int r = res == null ? 0 : res.intValue();

        if (r != 0)return r == 1 ? Result.fail("库存不足") : Result.fail("请勿重复下单");
        //为0，创建订单,把下单信息保存到阻塞队列
        long orderId = redisIdWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        //加入阻塞队列
        orderTasks.add(voucherOrder);
        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        return Result.ok(orderId);
    }

    /*
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //2.判断优惠券抢购是否开始或结束
        LocalDateTime now = LocalDateTime.now();
        if (seckillVoucher.getBeginTime().isAfter(now) || seckillVoucher.getEndTime().isBefore(now)){
            return Result.fail("抢购未开始或已结束");
        }
        //3.判断库存是否充足
        Integer stock = seckillVoucher.getStock();
        if (stock <= 0){
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        //获取锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        //尝试获取锁
        if (!lock.tryLock()) {
            return Result.fail("请勿重复下单");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.creatVoucherOrder(voucherId);
        }finally {
            //释放锁
            lock.unlock();
        }
    }
     */

    @Transactional
    public void creatVoucherOrder(VoucherOrder voucherOrder) {
        //写入数据库
        save(voucherOrder);
    }

    private void handleVoucher(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        //获取锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        //尝试获取锁
        boolean success;
        try {
            success = !lock.tryLock(1, 30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (success) {
            log.error("不允许重复下单");
            return;
        }

        try {
            proxy.creatVoucherOrder(voucherOrder);
        }finally {
            //释放锁
            lock.unlock();
        }
    }
}
