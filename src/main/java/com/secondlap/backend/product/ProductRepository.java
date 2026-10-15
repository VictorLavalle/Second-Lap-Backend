package com.secondlap.backend.product;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository
        extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByPublicId(UUID publicId);

    List<Product> findByCategoryIdAndStatusAndIdNot(
            Long categoryId, ProductStatus status, Long excludedId, Pageable pageable);

    /**
     * Atomic reservation: only the caller whose UPDATE flips a product that is
     * still 'disponible' wins. PostgreSQL serializes concurrent updates on the
     * row, so a second concurrent reservation re-evaluates the predicate after
     * the lock and affects zero rows. Returns the number of rows updated (1 or 0).
     */
    @Modifying
    @Query("""
            UPDATE Product p
            SET p.status = com.secondlap.backend.product.ProductStatus.APARTADO
            WHERE p.id = :id AND p.status = com.secondlap.backend.product.ProductStatus.DISPONIBLE
            """)
    int reserveIfAvailable(@Param("id") Long id);

    /**
     * Release a product back to 'disponible' when an order is cancelled. Guarded
     * so it never resurrects a product that was already reassigned.
     */
    @Modifying
    @Query("""
            UPDATE Product p
            SET p.status = com.secondlap.backend.product.ProductStatus.DISPONIBLE
            WHERE p.id = :id
              AND p.status IN (com.secondlap.backend.product.ProductStatus.APARTADO,
                               com.secondlap.backend.product.ProductStatus.VENDIDO)
            """)
    int releaseToAvailable(@Param("id") Long id);
}
