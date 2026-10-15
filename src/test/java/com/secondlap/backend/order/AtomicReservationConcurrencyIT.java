package com.secondlap.backend.order;

import com.secondlap.backend.AbstractPostgresIntegrationTest;
import com.secondlap.backend.common.ProductNotAvailableException;
import com.secondlap.backend.order.dto.CreateOrderRequest;
import com.secondlap.backend.order.dto.CustomerDetailsRequest;
import com.secondlap.backend.product.Product;
import com.secondlap.backend.product.ProductRepository;
import com.secondlap.backend.product.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AtomicReservationConcurrencyIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void exactlyOneOfTwoConcurrentReservationsWinsForTheSameProduct() throws Exception {
        // 'Jeans slim azul' ships as 'disponible' in the real seed.
        Product target = productRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProductStatus.DISPONIBLE)
                .findFirst()
                .orElseThrow();
        java.util.UUID productId = target.getPublicId();

        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        Callable<Void> attempt = () -> {
            CreateOrderRequest request = new CreateOrderRequest(
                    List.of(productId),
                    new CustomerDetailsRequest("Race Tester", "9991112222", null),
                    "recoger",
                    "efectivo",
                    null);
            startGate.await();
            try {
                orderService.createOrder(request);
                wins.incrementAndGet();
            } catch (ProductNotAvailableException expectedForLoser) {
                conflicts.incrementAndGet();
            }
            return null;
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Void> first = pool.submit(attempt);
        Future<Void> second = pool.submit(attempt);

        startGate.countDown(); // release both threads at once
        first.get();
        second.get();
        pool.shutdown();

        assertThat(wins.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);

        Product reloaded = productRepository.findByPublicId(productId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ProductStatus.APARTADO);
    }
}
