package com.secondlap.backend.product;

import com.secondlap.backend.product.dto.ProductFilter;
import com.secondlap.backend.product.dto.ProductResponse;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductResponse> getProducts(
            @RequestParam(required = false) UUID category,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String sort) {

        ProductFilter filter = new ProductFilter(
                category,
                size,
                condition != null ? ProductCondition.fromValue(condition) : null,
                status != null ? ProductStatus.fromValue(status) : null,
                maxPrice);

        return productService.findAll(filter, parseSort(sort));
    }

    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable UUID id) {
        return productService.findByPublicId(id);
    }

    @GetMapping("/{id}/similar")
    public List<ProductResponse> getSimilar(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "8") int limit) {
        return productService.findSimilar(id, limit);
    }

    private static final java.util.Set<String> SORTABLE =
            java.util.Set.of("id", "price", "createdAt", "modifiedDate");

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "id");
        }
        String[] parts = sort.split(",", 2);
        String property = parts[0].trim();
        if (!SORTABLE.contains(property)) {
            return Sort.by(Sort.Direction.DESC, "id");
        }
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
