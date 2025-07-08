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
package me.zhengjie.modules.security.security;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.modules.security.config.SecurityProperties;
import me.zhengjie.modules.security.service.dto.JwtUserDto;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import javax.servlet.http.HttpServletRequest;
import java.security.Key;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author /
 */
@Slf4j
@Component
public class TokenProvider implements InitializingBean {

    private Key signingKey;
    private JwtParser jwtParser;

    private final Cache<String, Long> tokenCache;
    private final SecurityProperties properties;

    public static final String AUTHORITIES_UUID_KEY = "uid";
    public static final String AUTHORITIES_UID_KEY = "userId";

    public TokenProvider(SecurityProperties properties) {
        this.properties = properties;
        // 初始化Caffeine缓存
        this.tokenCache = Caffeine.newBuilder()
                .maximumSize(10_000) // 设置最大缓存项
                .expireAfterWrite(properties.getTokenValidityInSeconds(), TimeUnit.SECONDS) // 全局过期时间
                .recordStats() // 记录统计信息
                .build();
    }

    @Override
    public void afterPropertiesSet() {
        byte[] keyBytes = Decoders.BASE64.decode(properties.getBase64Secret());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.jwtParser = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build();
    }

    public String createToken(JwtUserDto user) {
        Map<String, Object> claims = new HashMap<>(6);
        claims.put(AUTHORITIES_UID_KEY, user.getUser().getId());
        String uuid = IdUtil.simpleUUID();
        claims.put(AUTHORITIES_UUID_KEY, uuid);

        // 存入本地缓存替代Redis
        String loginKey = loginKeyInternal(user.getUsername(), uuid);
        tokenCache.put(loginKey, System.currentTimeMillis());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getUsername())
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public Authentication getAuthentication(String token) {
        Claims claims = getClaims(token);
        User principal = new User(claims.getSubject(), "******", new ArrayList<>());
        return new UsernamePasswordAuthenticationToken(principal, token, new ArrayList<>());
    }

    public Claims getClaims(String token) {
        return jwtParser.parseClaimsJws(token).getBody();
    }

    public void checkRenewal(String token) {
        String loginKey = loginKey(token);
        Long createTime = tokenCache.getIfPresent(loginKey);

        if (createTime != null) {
            long expireTime = createTime + properties.getTokenValidityInSeconds() * 1000;
            long differ = expireTime - System.currentTimeMillis();

            if (differ <= properties.getDetect()) {
                // 续期：更新创建时间
                tokenCache.put(loginKey, System.currentTimeMillis());
            }
        }
    }

    public String getToken(HttpServletRequest request) {
        final String requestHeader = request.getHeader(properties.getHeader());
        if (requestHeader != null && requestHeader.startsWith(properties.getTokenStartWith())) {
            return requestHeader.substring(7);
        }
        return null;
    }

    public String loginKey(String token) {
        Claims claims = getClaims(token);
        System.out.println(getClaims(token));
        return loginKeyInternal(claims.getSubject(), claims.get(AUTHORITIES_UUID_KEY).toString());
    }

    private String loginKeyInternal(String username, String uuid) {
        return properties.getOnlineKey() + username + ":" + uuid;
    }

    public String getId(String token) {
        Claims claims = getClaims(token);
        return claims.get(AUTHORITIES_UUID_KEY).toString();
    }

    // 新增方法：验证token是否有效
    public boolean validateToken(String token) {
        try {
            return tokenCache.getIfPresent(loginKey(token)) != null;
        } catch (Exception e) {
            log.error("Token验证失败", e);
            return false;
        }
    }

    // 获取缓存统计信息（监控用）
    public CacheStats getCacheStats() {
        return tokenCache.stats();
    }
}