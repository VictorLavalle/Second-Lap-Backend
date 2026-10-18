package com.secondlap.backend.product;

import com.secondlap.backend.product.dto.ProductFilter;
import com.secondlap.backend.product.dto.ProductResponse;
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
    public com.secondlap.backend.common.PageResponse<ProductResponse> getProducts(
            @RequestParam(required = false) UUID category,
            @RequestParam(required = false) java.util.List<String> size,
            @RequestParam(required = false) java.util.List<String> condition,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "pageSize", defaultValue = "12") int pageSize) {

        java.util.List<String> sizes = size == null ? java.util.List.of()
                : size.stream().filter(s -> s != null && !s.isBlank()).toList();
        java.util.List<ProductCondition> conditions = condition == null ? java.util.List.of()
                : condition.stream()
                        .filter(c -> c != null && !c.isBlank())
                        .map(ProductCondition::fromValue)
                        .toList();

        ProductFilter filter = new ProductFilter(
                category,
                sizes,
                conditions,
                status != null ? ProductStatus.fromValue(status) : null,
                maxPrice);

        return productService.findAll(filter, ProductSort.parse(sort), page, pageSize);
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
}
