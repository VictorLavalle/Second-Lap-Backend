package com.secondlap.backend.product;

import com.secondlap.backend.common.PageResponse;
import com.secondlap.backend.common.ResourceNotFoundException;
import com.secondlap.backend.product.dto.ProductFilter;
import com.secondlap.backend.product.dto.ProductResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private static final int DEFAULT_SIMILAR_LIMIT = 8;
    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 60;

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    public PageResponse<ProductResponse> findAll(ProductFilter filter, Sort sort, int page, int size) {
        Pageable pageable = PageRequest.of(clampPage(page), clampSize(size), sort);
        return PageResponse.from(
                productRepository.findAll(ProductSpecifications.fromFilter(filter), pageable)
                        .map(productMapper::toResponse));
    }

    // Clamp instead of rejecting: an out-of-range page/size is cosmetic, so it is
    // coerced (page<0 -> 0; size outside [1,MAX] -> default or MAX) rather than 400.
    private int clampPage(int page) {
        return Math.max(page, 0);
    }

    private int clampSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    public ProductResponse findByPublicId(UUID publicId) {
        return productMapper.toResponse(getEntityByPublicId(publicId));
    }

    public List<ProductResponse> findSimilar(UUID publicId, int limit) {
        Product product = getEntityByPublicId(publicId);
        PageRequest page = PageRequest.of(0, limit > 0 ? limit : DEFAULT_SIMILAR_LIMIT);
        return productRepository
                .findByCategoryIdAndStatusAndIdNot(
                        product.getCategory().getId(), ProductStatus.DISPONIBLE, product.getId(), page)
                .stream()
                .map(productMapper::toResponse)
                .toList();
    }

    private Product getEntityByPublicId(UUID publicId) {
        return productRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + publicId));
    }
}
