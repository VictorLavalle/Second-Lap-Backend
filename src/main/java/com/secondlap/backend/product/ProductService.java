package com.secondlap.backend.product;

import com.secondlap.backend.common.ResourceNotFoundException;
import com.secondlap.backend.product.dto.ProductFilter;
import com.secondlap.backend.product.dto.ProductResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private static final int DEFAULT_SIMILAR_LIMIT = 8;

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    public List<ProductResponse> findAll(ProductFilter filter, Sort sort) {
        return productRepository.findAll(ProductSpecifications.fromFilter(filter), sort).stream()
                .map(productMapper::toResponse)
                .toList();
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
