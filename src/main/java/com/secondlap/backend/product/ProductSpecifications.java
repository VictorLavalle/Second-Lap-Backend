package com.secondlap.backend.product;

import com.secondlap.backend.product.dto.ProductFilter;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> fromFilter(ProductFilter filter) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (filter.category() != null) {
                predicates.add(cb.equal(root.get("category").get("publicId"), filter.category()));
            }
            if (filter.sizes() != null && !filter.sizes().isEmpty()) {
                predicates.add(root.get("size").in(filter.sizes()));
            }
            if (filter.conditions() != null && !filter.conditions().isEmpty()) {
                predicates.add(root.get("condition").in(filter.conditions()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), filter.maxPrice()));
            }

            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
