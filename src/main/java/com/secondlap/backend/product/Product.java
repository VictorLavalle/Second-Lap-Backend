package com.secondlap.backend.product;

import com.secondlap.backend.category.Category;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @org.hibernate.annotations.Generated(event = org.hibernate.generator.EventType.INSERT)
    @Column(name = "public_id", nullable = false, updatable = false, insertable = false)
    private UUID publicId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "size", nullable = false)
    private String size;

    @Convert(converter = ProductConditionConverter.class)
    @Column(name = "condition", nullable = false)
    private ProductCondition condition;

    @Column(name = "brand")
    private String brand;

    @Column(name = "color")
    private String color;

    @Column(name = "measurements")
    private String measurements;

    @Convert(converter = ProductStatusConverter.class)
    @Column(name = "status", nullable = false)
    private ProductStatus status;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ProductImage> images = new ArrayList<>();

    protected Product() {
    }

    public Product(ProductDetails details) {
        apply(details);
        this.status = ProductStatus.DISPONIBLE;
    }

    public void update(ProductDetails details) {
        apply(details);
    }

    private void apply(ProductDetails details) {
        this.name = details.name();
        this.description = details.description();
        this.price = details.price();
        this.category = details.category();
        this.size = details.size();
        this.condition = details.condition();
        this.brand = details.brand();
        this.color = details.color();
        this.measurements = details.measurements();
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }

    public void addImage(String url) {
        images.add(new ProductImage(this, url, images.size()));
    }

    public void replaceImages(List<String> urls) {
        images.clear();
        for (int position = 0; position < urls.size(); position++) {
            images.add(new ProductImage(this, urls.get(position), position));
        }
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Category getCategory() {
        return category;
    }

    public String getSize() {
        return size;
    }

    public ProductCondition getCondition() {
        return condition;
    }

    public String getBrand() {
        return brand;
    }

    public String getColor() {
        return color;
    }

    public String getMeasurements() {
        return measurements;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public List<ProductImage> getImages() {
        return images;
    }
}
