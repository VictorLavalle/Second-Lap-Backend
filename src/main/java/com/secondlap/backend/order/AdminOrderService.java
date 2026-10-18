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
    public PageResponse<OrderResponse> findAll(String status, String deliveryMethod, String q,
                                               String sort, int page, int size) {
        OrderStatus statusFilter = status != null && !status.isBlank()
                ? OrderStatus.fromValue(status)
                : null;
        DeliveryMethod deliveryFilter = deliveryMethod != null && !deliveryMethod.isBlank()
                ? DeliveryMethod.fromValue(deliveryMethod)
                : null;
        String term = q != null && !q.isBlank() ? q.trim().toLowerCase() : null;

        org.springframework.data.jpa.domain.Specification<Order> spec = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            if (deliveryFilter != null) {
                predicates.add(cb.equal(root.get("deliveryMethod"), deliveryFilter));
            }
            if (term != null) {
                // Match the short order number (UUID prefix) OR the customer name
                // (contains). public_id is cast to text with a real SQL cast so a
                // non-UUID term never breaks the query — it just won't match by id.
                // Postgres renders a UUID as lowercase text, and 'term' is already
                // lowercased, so the id side needs no lower().
                jakarta.persistence.criteria.Expression<String> idAsText =
                        cb.function("str", String.class, root.get("publicId"));
                jakarta.persistence.criteria.Predicate byId = cb.like(cb.lower(idAsText), term + "%");
                jakarta.persistence.criteria.Predicate byName = cb.like(
                        cb.lower(root.get("customer").get("name")), "%" + term + "%");
                predicates.add(cb.or(byId, byName));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };

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
        return transition(getByPublicId(publicId), OrderStatus.CONFIRMED);
    }

    public OrderResponse complete(UUID publicId) {
        return transition(getByPublicId(publicId), OrderStatus.COMPLETED);
    }

    public OrderResponse deliver(UUID publicId) {
        return transition(getByPublicId(publicId), OrderStatus.DELIVERED);
    }

    public OrderResponse cancel(UUID publicId) {
        return transition(getByPublicId(publicId), OrderStatus.CANCELLED);
    }

    public OrderResponse changeStatus(UUID publicId, String statusValue) {
        return transition(getByPublicId(publicId), OrderStatus.fromValue(statusValue));
    }

    /**
     * The single, workflow-validated path for every order state change. Validates
     * the transition against {@link OrderStatus}'s allowed table, records the
     * timeline event with the acting admin, applies the target's inventory effect
     * and stamps its milestone.
     */
    private OrderResponse transition(Order order, OrderStatus target) {
        OrderStatus from = order.getStatus();
        if (!from.canTransitionTo(target)) {
            throw new InvalidOrderTransitionException(
                    "No se puede cambiar un pedido de " + from.getValue() + " a " + target.getValue());
        }
        String actor = currentAdminProvider.username();
        order.recordEvent(from, target, actor);
        order.setStatus(target);
        OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC);

        switch (target) {
            case CONFIRMED -> {
                order.setConfirmedAt(now);
                for (OrderItem item : order.getItems()) {
                    item.getProduct().setStatus(com.secondlap.backend.product.ProductStatus.VENDIDO);
                }
                log.info("admin '{}' confirmó el pedido {}: {} producto(s) marcados como vendido",
                        actor, order.getPublicId(), order.getItems().size());
            }
            case COMPLETED -> {
                order.setCompletedAt(now);
                log.info("admin '{}' completó el pedido {}", actor, order.getPublicId());
            }
            case DELIVERED -> {
                order.setDeliveredAt(now);
                log.info("admin '{}' entregó el pedido {}", actor, order.getPublicId());
            }
            case CANCELLED -> {
                order.setCancelledAt(now);
                int released = 0;
                for (OrderItem item : order.getItems()) {
                    released += productRepository.releaseToAvailable(item.getProduct().getId());
                }
                log.info("admin '{}' canceló el pedido {}: {} producto(s) liberados a disponible",
                        actor, order.getPublicId(), released);
            }
            default -> { /* PENDING is never a transition target */ }
        }
        return orderMapper.toResponse(order, storeProperties.whatsapp());
    }

    private Order getByPublicId(UUID publicId) {
        return orderRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + publicId));
    }
}
