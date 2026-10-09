// File: src/main/java/com/taskmanagement/config/RedisCacheConfig.java
package com.taskmanagement.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Cấu hình Redis làm cache provider cho Spring Cache Abstraction
 * (@Cacheable / @CachePut / @CacheEvict).
 *
 * @EnableCaching bật cơ chế proxy (AOP): Spring tự chặn các method có
 *                annotation cache và gọi vào CacheManager bên dưới. Bản thân
 *                CategoryService hoàn toàn không biết Redis tồn tại — nó chỉ
 *                khai báo
 *                "method này cần được cache", còn nơi lưu (Redis, bộ nhớ, ...)
 *                là một
 *                chi tiết được tiêm từ bên ngoài. Đây cũng là một biểu hiện
 *                khác của
 *                Inversion of Control, giống phần demo Greeter trước đó.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    /** TTL mặc định cho mọi cache chưa được khai báo override riêng. */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
            ObjectMapper springObjectMapper) {

        // Dùng lại chính ObjectMapper Spring Boot đã auto-config cho JSON
        // (đã có sẵn module đọc/ghi Instant, LocalDate...), rồi bật
        // "default typing" trên MỘT BẢN SAO của nó để Jackson ghi kèm
        // thông tin class (@class) vào JSON lưu trong Redis.
        //
        // Bắt buộc phải dùng DefaultTyping.EVERYTHING (không phải
        // NON_FINAL như hay thấy trong tài liệu cũ): các DTO trả về
        // (CategoryResponse...) là Java record, mà record luôn là lớp
        // "final". NON_FINAL sẽ bỏ qua không ghi type cho mọi record, và
        // lúc đọc lại từ cache, Spring sẽ không biết phải deserialize về
        // class nào, dẫn tới ClassCastException ngay khi cache hit.
        ObjectMapper redisObjectMapper = springObjectMapper.copy();
        redisObjectMapper.activateDefaultTyping(
                redisObjectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.EVERYTHING);

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                // Không cache giá trị null: tránh tình huống một id không
                // tồn tại bị "cache lại sự vắng mặt", khiến sau khi tạo
                // mới đúng id đó, client vẫn nhận null thêm một khoảng TTL.
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer));

        // Category là dữ liệu tham chiếu, ít thay đổi -> TTL dài hơn mặc định.
        Map<String, RedisCacheConfiguration> perCacheOverrides = Map.of("categories",
                defaultConfig.entryTtl(Duration.ofHours(1)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(perCacheOverrides)
                .build();
    }
}