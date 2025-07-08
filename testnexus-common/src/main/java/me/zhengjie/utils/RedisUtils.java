/*
 *  Copyright 2019-2025 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.zhengjie.utils;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author /
 */
@Component
@SuppressWarnings({"all"})
public class RedisUtils {
    private static final Logger log = LoggerFactory.getLogger(RedisUtils.class);

    private final Cache<String, Object> cache;

    public RedisUtils() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(1000) // 设置最大容量
                .expireAfterWrite(24, TimeUnit.HOURS) // 设置过期时间
                .build();
    }

    /**
     * 指定缓存失效时间
     *
     * @param key  键
     * @param time 时间(秒) 注意:这里将会替换原有的时间
     */
    public boolean expire(String key, long time) {
//        try {
//            if (time > 0) {
//                cache.put(key, cache.getIfPresent(key), time, TimeUnit.SECONDS);
//            }
//        } catch (Exception e) {
//            log.error(e.getMessage(), e);
//            return false;
//        }
//        return true;
        return false;
    }

    /**
     * 根据 key 获取过期时间
     *
     * @param key 键 不能为null
     * @return 时间(秒) 返回0代表为永久有效
     */
    public long getExpire(Object key) {
        // Caffeine does not provide a direct way to get the remaining expiration time
        return -1;
    }

    /**
     * 判断key是否存在
     *
     * @param key 键
     * @return true 存在 false不存在
     */
    public boolean hasKey(String key) {
        try {
            return cache.getIfPresent(key) != null;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return false;
        }
    }

    /**
     * 删除缓存
     *
     * @param key 可以传一个值 或多个
     */
    public void del(String... keys) {
        if (keys != null && keys.length > 0) {
            for (String key : keys) {
                cache.invalidate(key);
                log.debug("--------------------------------------------");
                log.debug(new StringBuilder("删除缓存：").append(key).append("，结果：").append(true).toString());
                log.debug("--------------------------------------------");
            }
        }
    }

    /**
     * 普通缓存获取
     *
     * @param key 键
     * @return 值
     */
    public Object get(String key) {
        return key == null ? null : cache.getIfPresent(key);
    }

    /**
     * 普通缓存获取
     *
     * @param key 键
     * @param clazz 值的类型
     * @return 值
     */
    public <T> T get(String key, Class<T> clazz) {
        Object value = key == null ? null : cache.getIfPresent(key);
        if (value == null) {
            return null;
        }
        // 如果 value 不是目标类型，则尝试将其反序列化为 clazz 类型
        if (!clazz.isInstance(value)) {
            return JSON.parseObject(value.toString(), clazz);
        } else if (clazz.isInstance(value)) {
            return clazz.cast(value);
        } else {
            return null;
        }
    }

    /**
     * 普通缓存放入
     *
     * @param key   键
     * @param value 值
     * @return true成功 false失败
     */
    public boolean set(String key, Object value) {
        try {
            cache.put(key, value);
            return true;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return false;
        }
    }

    /**
     * 普通缓存放入并设置时间
     *
     * @param key   键
     * @param value 值
     * @param time  时间(秒) time要大于0 如果time小于等于0 将设置无限期，注意:这里将会替换原有的时间
     * @return true成功 false 失败
     */
    public boolean set(String key, Object value, long time) {
        try {
            if (time > 0) {
                cache.put(key, value);
            } else {
                set(key, value);
            }
            return true;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return false;
        }
    }

    public List<String> scan(String keyPrefix) {
        List<String> entries = cache.asMap().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(keyPrefix))
//                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .map(entry -> entry.getKey())
                .collect(Collectors.toList());
        return entries;
    }

    public void scanDel(String keyPrefix) {
        List<String> keysToDelete = cache.asMap().keySet().stream()
                .filter(key -> key.startsWith(keyPrefix))
                .collect(Collectors.toList());
        keysToDelete.forEach(cache::invalidate);
    }

    public <T> List<T> getList(String key, Class<T> clazz) {
        Object value = key == null ? null : cache.getIfPresent(key);
        if (value == null) {
            return null;
        }
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            if (list.stream().allMatch(clazz::isInstance)) {
                return list.stream()
                        .map(clazz::cast)
                        .collect(Collectors.toList());
            }
        }
        return null;
    }

    public long delByKeys(String prefix, Set<Long> ids) {
        Set<String> keysToDelete = new HashSet<>();
        for (Long id : ids) {
            String key = prefix + id;
            keysToDelete.add(key);
        }
        return cache.asMap().keySet().removeAll(keysToDelete) ? keysToDelete.size() : 0;
    }
}
