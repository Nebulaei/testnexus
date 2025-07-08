///*
// *  Copyright 2019-2025 Zheng Jie
// *
// *  Licensed under the Apache License, Version 2.0 (the "License");
// *  you may not use this file except in compliance with the License.
// *  You may obtain a copy of the License at
// *
// *  http://www.apache.org/licenses/LICENSE-2.0
// *
// *  Unless required by applicable law or agreed to in writing, software
// *  distributed under the License is distributed on an "AS IS" BASIS,
// *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// *  See the License for the specific language governing permissions and
// *  limitations under the License.
// */
//package me.zhengjie.aspect;
//
//import cn.hutool.core.util.ObjUtil;
//import com.google.common.collect.ImmutableList;
//import me.zhengjie.annotation.Limit;
//import me.zhengjie.exception.BadRequestException;
//import me.zhengjie.utils.RequestHolder;
//import me.zhengjie.utils.StringUtils;
//import org.aspectj.lang.ProceedingJoinPoint;
//import org.aspectj.lang.annotation.Around;
//import org.aspectj.lang.annotation.Aspect;
//import org.aspectj.lang.annotation.Pointcut;
//import org.aspectj.lang.reflect.MethodSignature;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.data.redis.core.script.DefaultRedisScript;
//import org.springframework.data.redis.core.script.RedisScript;
//import org.springframework.stereotype.Component;
//import javax.servlet.http.HttpServletRequest;
//import java.lang.reflect.Method;
//
///**
// * @author /
// */
//@Aspect
//@Component
//public class LimitAspect {
//
//    private final RedisTemplate<Object,Object> redisTemplate;
//    private static final Logger logger = LoggerFactory.getLogger(LimitAspect.class);
//
//    public LimitAspect(RedisTemplate<Object,Object> redisTemplate) {
//        this.redisTemplate = redisTemplate;
//    }
//
//    @Pointcut("@annotation(me.zhengjie.annotation.Limit)")
//    public void pointcut() {
//    }
//
//    @Around("pointcut()")
//    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
//        HttpServletRequest request = RequestHolder.getHttpServletRequest();
//        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
//        Method signatureMethod = signature.getMethod();
//        Limit limit = signatureMethod.getAnnotation(Limit.class);
//        LimitType limitType = limit.limitType();
//        String key = limit.key();
//        if (StringUtils.isEmpty(key)) {
//            if (limitType == LimitType.IP) {
//                key = StringUtils.getIp(request);
//            } else {
//                key = signatureMethod.getName();
//            }
//        }
//
//        ImmutableList<Object> keys = ImmutableList.of(StringUtils.join(limit.prefix(), "_", key, "_", request.getRequestURI().replace("/","_")));
//
//        String luaScript = buildLuaScript();
//        RedisScript<Long> redisScript = new DefaultRedisScript<>(luaScript, Long.class);
//        Long count = redisTemplate.execute(redisScript, keys, limit.count(), limit.period());
//        if (ObjUtil.isNotNull(count) && count.intValue() <= limit.count()) {
//            logger.info("第{}次访问key为 {}，描述为 [{}] 的接口", count, keys, limit.name());
//            return joinPoint.proceed();
//        } else {
//            throw new BadRequestException("访问次数受限制");
//        }
//    }
//
//    /**
//     * 限流脚本
//     */
//    private String buildLuaScript() {
//        return "local c" +
//                "\nc = redis.call('get',KEYS[1])" +
//                "\nif c and tonumber(c) > tonumber(ARGV[1]) then" +
//                "\nreturn c;" +
//                "\nend" +
//                "\nc = redis.call('incr',KEYS[1])" +
//                "\nif tonumber(c) == 1 then" +
//                "\nredis.call('expire',KEYS[1],ARGV[2])" +
//                "\nend" +
//                "\nreturn c;";
//    }
//}



/*
 * Copyright 2019-2025 Zheng Jie
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.zhengjie.aspect;

import cn.hutool.core.util.ObjUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import me.zhengjie.annotation.Limit;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.utils.RequestHolder;
import me.zhengjie.utils.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caffeine实现的限流切面
 */
@Aspect
@Component
public class LimitAspect {

    private static final Logger logger = LoggerFactory.getLogger(LimitAspect.class);

    // 使用Caffeine缓存存储计数器
    private final Cache<String, AtomicLong> counterCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(1, TimeUnit.HOURS) // 条目1小时未访问自动清除
            .build();

    @Pointcut("@annotation(me.zhengjie.annotation.Limit)")
    public void pointcut() {
    }

    @Around("pointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = RequestHolder.getHttpServletRequest();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Limit limit = method.getAnnotation(Limit.class);

        // 生成限流key
        String key = generateKey(limit, request, method);

        // 获取当前计数
        AtomicLong counter = counterCache.get(key, k -> new AtomicLong(0));
        if (counter == null) {
            throw new IllegalStateException("Failed to get or create counter");
        }

        // 检查限流
        long currentCount = counter.incrementAndGet();
        if (currentCount == 1) {
            // 首次访问设置过期时间
            counterCache.policy().expireVariably().ifPresent(policy -> {
                policy.put(key, counter, limit.period(), TimeUnit.SECONDS);
            });
        }

        if (currentCount > limit.count()) {
            throw new BadRequestException("访问次数受限制");
        }

        logger.info("第{}次访问key为 {}，描述为 [{}] 的接口", currentCount, key, limit.name());
        return joinPoint.proceed();
    }

    /**
     * 生成限流key
     */
    private String generateKey(Limit limit, HttpServletRequest request, Method method) {
        String key = limit.key();
        if (StringUtils.isEmpty(key)) {
            if (limit.limitType() == LimitType.IP) {
                key = StringUtils.getIp(request);
            } else {
                key = method.getName();
            }
        }
        return StringUtils.join(limit.prefix(), "_", key, "_",
                request.getRequestURI().replace("/", "_"));
    }
}
