package com.secondlap.backend.order;

import com.secondlap.backend.common.ResourceNotFoundException;
import com.secondlap.backend.common.InvalidOrderTransitionException;
import com.secondlap.backend.admin.CurrentAdminProvider;
import com.secondlap.backend.common.PageResponse;
import com.secondlap.backend.config.StoreProperties;
import com.secondlap.backend.order.dto.OrderResponse;
import com.secondlap.backend.product.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Transactional
public class AdminOrderService {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;
    private final StoreProperties storeProperties;
    private final CurrentAdminProvider currentAdminProvider;

    public AdminOrderService(OrderRepository orderRepository,
                             ProductRepository productRepository,
                             OrderMapper orderMapper,
                             StoreProperties storeProperties,
                             CurrentAdminProvider currentAdminProvider) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.orderMapper = orderMapper;
        this.storeProperties = storeProperties;
        this.currentAdminProvider = currentAdminProvider;
    }

    private static final java.util.Set<String> SORTABLE =
            java.util.Set.of("createdAt", "total", "status");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> findAll(String status, String sort, int page, int size) {
        OrderStatus statusFilter = status != null && !status.isBlank()
                ? OrderStatus.fromValue(status)
                : null;
        org.springframework.data.jpa.domain.Specification<Order> spec =
                (root, query, cb) -> statusFilter == null
                        ? cb.conjunction()
                        : cb.equal(root.get("status"), statusFilter);
        org.springframework.data.domain.Pageable pageable =
                com.secondlap.backend.common.PageSupport.toPageable(page, size, parseSort(sort));
        return PageResponse.from(
                orderRepository.findAll(spec, pageable)
                        .map(order -> orderMapper.toResponse(order, storeProperties.whatsapp())));
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_SORT;
        }
        String[] parts = sort.split(",", 2);
        String property = parts[0].trim();
        if (!SORTABLE.contains(property)) {
            return DEFAULT_SORT;
        }
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }

    @Transactional(readOnly = true)
    public OrderResponse findByPublicId(UUID publicId) {
        return orderMapper.toResponse(getByPublicId(publicId), storeProperties.whatsapp());
    }

    public OrderResponse confirm(UUID publicId) {
        Order order = getByPublicId(publicId);
        requireStatus(order, OrderStatus.PENDING, "confirmar");
        String actor = transitionTo(order, OrderStatus.CONFIRMED);
        order.setConfirmedAt(OffsetDateTime.now(java.time.ZoneOffset.UTC));
        for (OrderItem item : order.getItems()) {
            item.getProduct().setStatus(com.secondlap.backend.product.ProductStatus.VENDIDO);
        }
        log.info("admin '{}' confirmó el pedido {}: {} producto(s) marcados como vendido",
                actor, publicId, order.getItems().size());
        return orderMapper.toResponse(order, storeProperties.whatsapp());
    }

    public OrderResponse complete(UUID publicId) {
        Order order = getByPublicId(publicId);
        requireStatus(order, OrderStatus.CONFIRMED, "completar");
        String actor = transitionTo(order, OrderStatus.COMPLETED);
        order.setCompletedAt(OffsetDateTime.now(java.time.ZoneOffset.UTC));
        log.info("admin '{}' completó el pedido {}", actor, publicId);
        return orderMapper.toResponse(order, storeProperties.whatsapp());
    }

    public OrderResponse deliver(UUID publicId) {
        Order order = getByPublicId(publicId);
        requireStatus(order, OrderStatus.COMPLETED, "entregar");
        String actor = transitionTo(order, OrderStatus.DELIVERED);
        order.setDeliveredAt(OffsetDateTime.now(java.time.ZoneOffset.UTC));
        log.info("admin '{}' entregó el pedido {}", actor, publicId);
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
        String actor = transitionTo(order, OrderStatus.CANCELLED);
        order.setCancelledAt(OffsetDateTime.now(java.time.ZoneOffset.UTC));
        int released = 0;
        for (OrderItem item : order.getItems()) {
            released += productRepository.releaseToAvailable(item.getProduct().getId());
        }
        log.info("admin '{}' canceló el pedido {}: {} producto(s) liberados a disponible",
                actor, publicId, released);
        return orderMapper.toResponse(order, storeProperties.whatsapp());
    }

    private String transitionTo(Order order, OrderStatus target) {
        String actor = currentAdminProvider.username();
        order.recordEvent(order.getStatus(), target, actor);
        order.setStatus(target);
        return actor;
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
