package com.iflytek.interview.common.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 配置：Key 用字符串序列化，Value 用 JSON（GenericJackson2JsonRedisSerializer）
 * 修复：注册 JavaTimeModule 以支持 LocalDateTime 等 Java 8 日期时间类型的序列化。
 */
@Configuration
public class RedisConfig {

    /**
     * 创建带 JavaTimeModule 的 JSON 序列化器。
     * GenericJackson2JsonRedisSerializer 默认的 ObjectMapper 不含 JSR-310 模块，
     * 遇到 LocalDateTime / LocalDate / Instant 等字段会抛出
     * "Java 8 date/time type not supported by default" 异常。
     */
    private GenericJackson2JsonRedisSerializer createJsonSerializer() {
        ObjectMapper om = new ObjectMapper();
        // 所有字段可见（包括 private），确保实体属性完整序列化
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 保留类型信息（@class 属性），反序列化时还原为原始类型
        om.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        // 注册 Java 8 日期时间模块（核心修复）
        om.registerModule(new JavaTimeModule());
        // 日期输出为 ISO-8601 字符串（如 "2026-09-08T10:30:00"），而不是 [2026,9,8,10,30,0] 数组
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 反序列化时忽略未知字段，避免新增字段后旧缓存反序列化失败
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return new GenericJackson2JsonRedisSerializer(om);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = createJsonSerializer();

        template.setKeySerializer(stringSerializer);      // key：字符串
        template.setValueSerializer(jsonSerializer);      // value：JSON（自带类型信息，可还原成对象）
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 缓存管理器：给 @Cacheable / @CacheEvict 提供读写 Redis 的能力。
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        GenericJackson2JsonRedisSerializer jsonSerializer = createJsonSerializer();

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer))
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }
}
