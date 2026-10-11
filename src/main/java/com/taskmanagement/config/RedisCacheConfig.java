// File: src/main/java/com/taskmanagement/config/RedisCacheConfig.java
package com.taskmanagement.config;

import com.taskmanagement.dto.CategoryResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
            JsonMapper mapper) {
        RedisSerializer<Object> jsonSerializer = new CategoryCacheSerializer(mapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                // Keep old Jackson 2 cache entries separate during the upgrade.
                .computePrefixWith(cacheName -> "boot4::" + cacheName + "::")
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

    /** The only cached values are a category DTO or a list of category DTOs. */
    private static final class CategoryCacheSerializer implements RedisSerializer<Object> {
        private static final TypeReference<List<CategoryResponse>> LIST_TYPE = new TypeReference<>() {};
        private final JsonMapper mapper;

        private CategoryCacheSerializer(JsonMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public byte[] serialize(Object value) throws SerializationException {
            if (value == null) return new byte[0];
            if (!(value instanceof CategoryResponse)
                    && !(value instanceof List<?> list && list.stream().allMatch(CategoryResponse.class::isInstance))) {
                throw new SerializationException("Unexpected category cache value type");
            }
            try {
                return mapper.writeValueAsBytes(value);
            } catch (JacksonException exception) {
                throw new SerializationException("Cannot serialize category cache value", exception);
            }
        }

        @Override
        public Object deserialize(byte[] source) throws SerializationException {
            if (source == null || source.length == 0) return null;
            try {
                JsonNode value = mapper.readTree(source);
                return value.isArray() ? mapper.readValue(source, LIST_TYPE)
                        : mapper.readValue(source, CategoryResponse.class);
            } catch (JacksonException exception) {
                throw new SerializationException("Cannot deserialize category cache value", exception);
            }
        }
    }
}
