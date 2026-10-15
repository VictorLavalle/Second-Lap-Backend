package com.secondlap.backend.product;

import com.secondlap.backend.category.Category;
import com.secondlap.backend.category.CategoryRepository;
import com.secondlap.backend.common.ResourceNotFoundException;
import com.secondlap.backend.product.dto.AdminProductRequest;
import com.secondlap.backend.product.dto.ProductResponse;
import com.secondlap.backend.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AdminProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final StorageService storageService;

    public AdminProductService(ProductRepository productRepository,
                               CategoryRepository categoryRepository,
                               ProductMapper productMapper,
                               StorageService storageService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMapper = productMapper;
        this.storageService = storageService;
    }

    public ProductResponse create(AdminProductRequest request) {
        Category category = resolveCategory(request.categoryId());
        Product product = new Product(toDetails(request, category));
        return productMapper.toResponse(productRepository.save(product));
    }

    public ProductResponse update(UUID publicId, AdminProductRequest request) {
        Product product = getByPublicId(publicId);
        Category category = resolveCategory(request.categoryId());
        product.update(toDetails(request, category));
        return productMapper.toResponse(product);
    }

    private ProductDetails toDetails(AdminProductRequest request, Category category) {
        return new ProductDetails(
                request.name(), request.description(), request.price(), category,
                request.size(), ProductCondition.fromValue(request.condition()),
                request.brand(), request.color(), request.measurements());
    }

    public void delete(UUID publicId) {
        Product product = getByPublicId(publicId);
        productRepository.delete(product);
    }

    public ProductResponse updateStatus(UUID publicId, String status) {
        Product product = getByPublicId(publicId);
        product.setStatus(ProductStatus.fromValue(status));
        return productMapper.toResponse(product);
    }

    public ProductResponse addImages(UUID publicId, List<MultipartFile> files) {
        Product product = getByPublicId(publicId);
        for (MultipartFile file : files) {
            product.addImage(storageService.store(file));
        }
        return productMapper.toResponse(product);
    }

    private Product getByPublicId(UUID publicId) {
        return productRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + publicId));
    }

    private Category resolveCategory(UUID categoryPublicId) {
        return categoryRepository.findByPublicId(categoryPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryPublicId));
    }
}
