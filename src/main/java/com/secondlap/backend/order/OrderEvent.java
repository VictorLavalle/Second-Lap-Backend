package com.secondlap.backend.order;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_event")
public class OrderEvent {

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

    @Convert(converter = OrderStatusConverter.class)
    @Column(name = "from_status")
    private OrderStatus fromStatus;

    @Convert(converter = OrderStatusConverter.class)
    @Column(name = "to_status", nullable = false)
    private OrderStatus toStatus;

    @Generated(event = EventType.INSERT)
    @Column(name = "occurred_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime occurredAt;

    protected OrderEvent() {
    }

    public OrderEvent(Order order, OrderStatus fromStatus, OrderStatus toStatus) {
        this.order = order;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Order getOrder() {
        return order;
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}
