package com.secondlap.backend.order;

import com.secondlap.backend.common.RateLimitExceededException;
import com.secondlap.backend.config.StoreProperties;
import com.secondlap.backend.customer.CustomerService;
import com.secondlap.backend.neighborhood.NeighborhoodRepository;
import com.secondlap.backend.order.dto.CreateOrderRequest;
import com.secondlap.backend.order.dto.CustomerDetailsRequest;
import com.secondlap.backend.product.ProductRepository;
import com.secondlap.backend.ratelimit.RateLimitService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final NeighborhoodRepository neighborhoodRepository = mock(NeighborhoodRepository.class);
    private final CustomerService customerService = mock(CustomerService.class);
    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final RateLimitService rateLimitService = mock(RateLimitService.class);

    private OrderService service() {
        return new OrderService(productRepository, orderRepository, neighborhoodRepository,
                customerService, orderMapper, new StoreProperties("529996384026"),
                new OrderProperties(20), rateLimitService);
    }

    private CreateOrderRequest request(int itemCount) {
        List<UUID> ids = java.util.stream.IntStream.range(0, itemCount)
                .mapToObj(i -> UUID.randomUUID()).toList();
        return new CreateOrderRequest(ids,
                new CustomerDetailsRequest("Ana", "9991234567", null),
                "recoger", "efectivo", null);
    }

    @Test
    void throwsRateLimitAndDoesNotReserveWhenWhatsappLimitExceeded() {
        when(rateLimitService.tryConsumeOrderByWhatsapp(any())).thenReturn(false);
        OrderService service = service();
        CreateOrderRequest request = request(1);

        assertThatThrownBy(() -> service.createOrder(request))
                .isInstanceOf(RateLimitExceededException.class);

        verify(productRepository, never()).reserveIfAvailable(any());
    }

    @Test
    void rejectsOrderExceedingMaxItemsBeforeTouchingAnything() {
        OrderService service = service();
        CreateOrderRequest request = request(21);

        assertThatThrownBy(() -> service.createOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than 20");

        verify(rateLimitService, never()).tryConsumeOrderByWhatsapp(any());
        verify(productRepository, never()).reserveIfAvailable(any());
    }
}
