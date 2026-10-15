package com.secondlap.backend.product;

import com.secondlap.backend.product.dto.ProductResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        List<String> imageUrls = product.getImages().stream()
                .map(ProductImage::getUrl)
                .toList();

        return new ProductResponse(
                product.getPublicId().toString(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                imageUrls,
                product.getCategory().getPublicId().toString(),
                product.getSize(),
                product.getCondition().getValue(),
                product.getBrand(),
                product.getColor(),
                product.getMeasurements(),
                product.getStatus().getValue());
    }
}
