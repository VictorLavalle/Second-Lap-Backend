package com.secondlap.backend.order;

import com.secondlap.backend.order.dto.OrderResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    @GetMapping
    public List<OrderResponse> getOrders() {
        return adminOrderService.findAll();
    }

    @PatchMapping("/{id}/confirm")
    public OrderResponse confirm(@PathVariable UUID id) {
        return adminOrderService.confirm(id);
    }

    @PatchMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id) {
        return adminOrderService.cancel(id);
    }
}
