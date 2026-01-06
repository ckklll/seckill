package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)){
            //不符合返回错误信息
            return Result.fail("手机格式错误！");
        }
        //符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //将验证码存入session
        redisTemplate.opsForValue().set("login:code:"+phone,code,5, TimeUnit.MINUTES);
        //发送验证码
        log.info("发送验证码成功:{}",code);
        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            //不符合返回错误信息
            return Result.fail("手机格式错误！");
        }
        //校验验证码
        String cacheCode = redisTemplate.opsForValue().get("login:code:"+phone);
        String code = loginForm.getCode();
        if (!code.equals(cacheCode)){
            return Result.fail("验证码错误！");
        }
        //根据手机号查询用户信息
        User user = query().eq("phone", phone).one();
        //判断用户是否存在
        if (user == null){
            //如果没有创建用户并保存
            user = createUserWithPhone(phone);
        }
        //1.生成随机token，作为登录令牌，
        String token = UUID.randomUUID().toString();
        //2.将user转为Hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                          .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue)->(fieldValue.toString())));
        //3.存储
        redisTemplate.opsForHash().putAll("login:token:"+token,userMap);
        redisTemplate.expire("login:token:"+token,30,TimeUnit.MINUTES);
        //返回ok
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomNumbers(10));
        //2.保存用户
        save(user);
        return user;
    }
}
