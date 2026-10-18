package com.secondlap.backend.order;

import com.secondlap.backend.order.dto.OrderResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    @GetMapping
    public com.secondlap.backend.common.PageResponse<OrderResponse> getOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String deliveryMethod,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return adminOrderService.findAll(status, deliveryMethod, q, sort, page, size);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable UUID id) {
        return adminOrderService.findByPublicId(id);
    }

    @PatchMapping("/{id}/confirm")
    public OrderResponse confirm(@PathVariable UUID id) {
        return adminOrderService.confirm(id);
    }

    @PatchMapping("/{id}/complete")
    public OrderResponse complete(@PathVariable UUID id) {
        return adminOrderService.complete(id);
    }

    @PatchMapping("/{id}/deliver")
    public OrderResponse deliver(@PathVariable UUID id) {
        return adminOrderService.deliver(id);
    }

    @PatchMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id) {
        return adminOrderService.cancel(id);
    }
}
