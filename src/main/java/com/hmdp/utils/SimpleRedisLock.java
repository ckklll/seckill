package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.Resource;
import javax.swing.*;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    private String name;
    private static final String PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true);
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name) {
        this.name = name;
    }

    public boolean tryLock(long lockSec){
        String id = ID_PREFIX+Thread.currentThread().getId();
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(PREFIX + name, id, lockSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }



    public void unlock(){
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(PREFIX+name),
                ID_PREFIX+Thread.currentThread().getId()
                );
    }

    /*
    public void unlock(){
        //调用lua脚本
        String curId = ID_PREFIX+Thread.currentThread().getId();
        if (curId.equals(stringRedisTemplate.opsForValue().get(PREFIX+"name"))){
            stringRedisTemplate.delete(PREFIX+name);
        }
    }

     */


}
