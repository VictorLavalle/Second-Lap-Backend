package com.secondlap.backend.order;

import com.secondlap.backend.common.ResourceNotFoundException;
import com.secondlap.backend.common.InvalidOrderTransitionException;
import com.secondlap.backend.config.StoreProperties;
import com.secondlap.backend.order.dto.OrderResponse;
import com.secondlap.backend.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AdminOrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;
    private final StoreProperties storeProperties;

    public AdminOrderService(OrderRepository orderRepository,
                             ProductRepository productRepository,
                             OrderMapper orderMapper,
                             StoreProperties storeProperties) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.orderMapper = orderMapper;
        this.storeProperties = storeProperties;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(order -> orderMapper.toResponse(order, storeProperties.whatsapp()))
                .toList();
    }

    public OrderResponse confirm(UUID publicId) {
        Order order = getByPublicId(publicId);
        requireStatus(order, OrderStatus.PENDING, "confirmar");
        transitionTo(order, OrderStatus.CONFIRMED);
        order.setConfirmedAt(OffsetDateTime.now());
        for (OrderItem item : order.getItems()) {
            item.getProduct().setStatus(com.secondlap.backend.product.ProductStatus.VENDIDO);
        }
        return orderMapper.toResponse(order, storeProperties.whatsapp());
    }

    public OrderResponse complete(UUID publicId) {
        Order order = getByPublicId(publicId);
        requireStatus(order, OrderStatus.CONFIRMED, "completar");
        transitionTo(order, OrderStatus.COMPLETED);
        order.setCompletedAt(OffsetDateTime.now());
        return orderMapper.toResponse(order, storeProperties.whatsapp());
    }

    public OrderResponse deliver(UUID publicId) {
        Order order = getByPublicId(publicId);
        requireStatus(order, OrderStatus.COMPLETED, "entregar");
        transitionTo(order, OrderStatus.DELIVERED);
        order.setDeliveredAt(OffsetDateTime.now());
        return orderMapper.toResponse(order, storeProperties.whatsapp());
    }

    /**
     * Cancel an order and release its items back to 'disponible'. The release uses
     * the guarded conditional UPDATE so a product already reassigned to another
     * order is never resurrected.
     */
    public OrderResponse cancel(UUID publicId) {
        Order order = getByPublicId(publicId);
        requireStatus(order, OrderStatus.PENDING, "cancelar");
        transitionTo(order, OrderStatus.CANCELLED);
        order.setCancelledAt(OffsetDateTime.now());
        for (OrderItem item : order.getItems()) {
            productRepository.releaseToAvailable(item.getProduct().getId());
        }
        return orderMapper.toResponse(order, storeProperties.whatsapp());
    }

    private void transitionTo(Order order, OrderStatus target) {
        order.recordEvent(order.getStatus(), target);
        order.setStatus(target);
    }

    private void requireStatus(Order order, OrderStatus expected, String action) {
        if (order.getStatus() != expected) {
            throw new InvalidOrderTransitionException(
                    "No se puede " + action + " un pedido en estado " + order.getStatus().getValue());
        }
    }

    private Order getByPublicId(UUID publicId) {
        return orderRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + publicId));
    }
}
