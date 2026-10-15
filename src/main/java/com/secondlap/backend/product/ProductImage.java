package com.secondlap.backend.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_image")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "position", nullable = false)
    private int position;

    protected ProductImage() {
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getUrl() {
        return url;
    }

    public int getPosition() {
        return position;
    }
}
