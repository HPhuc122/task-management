package com.taskmanagement.service;

import com.taskmanagement.dto.CategoryRequest;
import com.taskmanagement.dto.CategoryResponse;
import com.taskmanagement.entity.Category;
import com.taskmanagement.repository.CategoryRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Category là dữ liệu tham chiếu (reference data): ít bản ghi, đọc nhiều,
 * ghi hiếm -> ứng viên lý tưởng cho caching. Mỗi method dưới đây chỉ khai
 * báo Ý ĐỊNH cache (đọc từ cache, ghi vào cache, hay xoá khỏi cache) qua
 * annotation; RedisCacheConfig quyết định cache đó thực sự nằm ở đâu.
 */
@Service
public class CategoryService {

    private static final String CACHE_NAME = "categories";
    private static final String ALL_KEY = "'all'";

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CACHE_NAME, key = ALL_KEY)
    public List<CategoryResponse> getAll() {
        // .collect(Collectors.toList()) (KHÔNG dùng .toList()): Stream.toList()
        // trả về một lớp List nội bộ của JDK mà Jackson không thể khởi tạo lại
        // khi deserialize từ cache. Collectors.toList() trả về ArrayList bình
        // thường, Jackson xử lý được.
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CACHE_NAME, key = "#id")
    public CategoryResponse getById(Long id) {
        return categoryRepository.findById(id)
                .map(CategoryResponse::fromEntity)
                .orElseThrow(() -> notFound(id));
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_NAME, key = ALL_KEY)
    public CategoryResponse create(CategoryRequest request) {
        Category category = new Category();
        category.setName(request.name());
        category.setDescription(request.description());

        // id/createdAt/updatedAt được @Generated ở BaseEntity tự nạp lại
        // ngay sau câu INSERT, không cần gọi thêm thao tác nào khác.
        Category saved = categoryRepository.save(category);
        return CategoryResponse.fromEntity(saved);
    }

    @Transactional
    @Caching(put = { @CachePut(cacheNames = CACHE_NAME, key = "#id") }, evict = {
            @CacheEvict(cacheNames = CACHE_NAME, key = ALL_KEY) })
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category existing = categoryRepository.findById(id)
                .orElseThrow(() -> notFound(id));

        existing.setName(request.name());
        existing.setDescription(request.description());

        Category saved = categoryRepository.save(existing);
        // Giá trị trả về chính là thứ được @CachePut ghi đè vào key #id,
        // nên cache luôn khớp với DB ngay sau khi update, không cần evict
        // rồi chờ lần đọc kế tiếp mới nạp lại.
        return CategoryResponse.fromEntity(saved);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CACHE_NAME, key = "#id"),
            @CacheEvict(cacheNames = CACHE_NAME, key = ALL_KEY)
    })
    public void delete(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw notFound(id);
        }
        categoryRepository.deleteById(id);
    }

    private ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Category " + id + " không tồn tại");
    }
}