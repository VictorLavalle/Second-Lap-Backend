package com.secondlap.backend.category;

import com.secondlap.backend.category.dto.CategoryResponse;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getPublicId().toString(),
                category.getName(),
                category.getSortOrder());
    }
}
