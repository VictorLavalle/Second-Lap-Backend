package com.secondlap.backend.order;

import com.secondlap.backend.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", nullable = false, updatable = false, insertable = false)
    private UUID publicId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "price_snapshot", nullable = false)
    private BigDecimal priceSnapshot;

    protected OrderItem() {
    }

    public OrderItem(Order order, Product product, BigDecimal priceSnapshot) {
        this.order = order;
        this.product = product;
        this.priceSnapshot = priceSnapshot;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Order getOrder() {
        return order;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getPriceSnapshot() {
        return priceSnapshot;
    }
}
