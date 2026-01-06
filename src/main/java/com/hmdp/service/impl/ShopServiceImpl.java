package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        //1.查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstant.CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(shopJson)){
            //2.如果缓存命中，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        if (shopJson != null){
            return Result.fail("店铺信息不存在！");
        }
        //3.缓存未命中，查询数据库
        Shop shop = getById(id);
        //4.如果不存在，返回错误
        if (shop == null){
            stringRedisTemplate.opsForValue().set(RedisConstant.CACHE_SHOP_KEY+id,"",RedisConstant.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return Result.fail("商户不存在");
        }
        //5.写入缓存
        stringRedisTemplate.opsForValue().set(RedisConstant.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop),RedisConstant.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Object update(Shop shop) {
        //更新数据库
        updateById(shop);
        //删除缓存
        Long id = shop.getId();
        if (id == null){
            return Result.fail("店铺id不能为空");
        }
        stringRedisTemplate.delete(RedisConstant.CACHE_SHOP_KEY+id);
        return Result.ok();
    }
}
