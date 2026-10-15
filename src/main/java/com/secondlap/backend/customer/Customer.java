package com.secondlap.backend.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.util.UUID;

@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", nullable = false, updatable = false, insertable = false)
    private UUID publicId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "whatsapp", nullable = false, updatable = false)
    private String whatsapp;

    @Column(name = "comments")
    private String comments;

    protected Customer() {
    }

    public Customer(String name, String whatsapp, String comments) {
        this.name = name;
        this.whatsapp = whatsapp;
        this.comments = comments;
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

    public void setName(String name) {
        this.name = name;
    }

    public String getWhatsapp() {
        return whatsapp;
    }

    public String getComments() {
        return comments;
    }
}
