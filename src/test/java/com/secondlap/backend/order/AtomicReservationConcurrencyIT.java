package com.secondlap.backend.order;

import com.secondlap.backend.AbstractPostgresIntegrationTest;
import com.secondlap.backend.common.ProductNotAvailableException;
import com.secondlap.backend.customer.CustomerRepository;
import com.secondlap.backend.order.dto.CreateOrderRequest;
import com.secondlap.backend.order.dto.CustomerDetailsRequest;
import com.secondlap.backend.product.Product;
import com.secondlap.backend.product.ProductRepository;
import com.secondlap.backend.product.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AtomicReservationConcurrencyIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private long countOrderItems() {
        return transactionTemplate.execute(status ->
                orderRepository.findAll().stream()
                        .mapToLong(o -> o.getItems().size())
                        .sum());
    }

    private UUID firstAvailableProductId() {
        return productRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProductStatus.DISPONIBLE)
                .map(Product::getPublicId)
                .findFirst()
                .orElseThrow();
    }

    private CreateOrderRequest pickupOrder(List<UUID> productIds, String whatsapp) {
        return new CreateOrderRequest(productIds,
                new CustomerDetailsRequest("Race Tester", whatsapp, null),
                "recoger", "efectivo", null);
    }

    @Test
    void exactlyOneOfManyConcurrentReservationsWinsForTheSameProduct() throws Exception {
        int threads = 25;
        UUID productId = firstAvailableProductId();

        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Future<?>[] futures = new Future<?>[threads];
        for (int i = 0; i < threads; i++) {
            // Distinct WhatsApp per thread so the per-number rate limit never
            // masks the reservation race we are actually testing.
            String whatsapp = String.format("99%08d", i);
            futures[i] = pool.submit(() -> {
                startGate.await();
                try {
                    orderService.createOrder(pickupOrder(List.of(productId), whatsapp));
                    wins.incrementAndGet();
                } catch (ProductNotAvailableException _) {
                    conflicts.incrementAndGet();
                } catch (RuntimeException _) {
                    unexpected.incrementAndGet();
                }
                return null;
            });
        }
        startGate.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // Exactly one winner, the rest 409, no corrupt intermediate state.
        assertThat(wins.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(threads - 1);
        assertThat(unexpected.get()).isZero();

        assertThat(productRepository.findByPublicId(productId).orElseThrow().getStatus())
                .isEqualTo(ProductStatus.APARTADO);
        // exactly one order (one order_item) materialized for this product
        assertThat(orderRepository.findAll()).hasSize(1);
        assertThat(countOrderItems()).isEqualTo(1);
    }

    @Test
    void overlappingMultiItemOrdersInDifferentOrderDoNotDeadlockAndNeverPartiallyReserve()
            throws Exception {
        List<UUID> available = productRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProductStatus.DISPONIBLE)
                .map(Product::getPublicId)
                .limit(3)
                .toList();
        UUID a = available.get(0);
        UUID b = available.get(1);
        UUID c = available.get(2);

        // Two orders sharing {a, b} but listing them in opposite order; the
        // service locks by internal id ascending, so no deadlock is possible.
        CreateOrderRequest order1 = pickupOrder(List.of(a, b, c), "9991110001");
        CreateOrderRequest order2 = pickupOrder(List.of(b, a), "9991110002");

        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        Callable<Void> run1 = () -> runOne(startGate, order1, wins, conflicts);
        Callable<Void> run2 = () -> runOne(startGate, order2, wins, conflicts);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> f1 = pool.submit(run1);
        Future<?> f2 = pool.submit(run2);
        startGate.countDown();
        f1.get(30, TimeUnit.SECONDS); // would time out on a deadlock
        f2.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        // One order wins, the other conflicts on a shared item and rolls back fully.
        assertThat(wins.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);

        // No product is left half-reserved: the loser released everything it touched.
        long reserved = List.of(a, b, c).stream()
                .map(id -> productRepository.findByPublicId(id).orElseThrow())
                .filter(p -> p.getStatus() == ProductStatus.APARTADO)
                .count();
        // The winner is either order1 (reserves a,b,c => 3) or order2 (reserves a,b => 2).
        assertThat(reserved).isIn(2L, 3L);
        assertThat(orderRepository.findAll()).hasSize(1);
    }

    @Test
    void concurrentOrdersWithSameNewWhatsappCreateExactlyOneCustomer() throws Exception {
        List<UUID> available = productRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProductStatus.DISPONIBLE)
                .map(Product::getPublicId)
                .limit(2)
                .toList();
        String whatsapp = "9994443322";
        long customersBefore = customerRepository.count();

        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        Callable<Void> attempt1 = () -> runGetOrCreate(startGate,
                pickupOrder(List.of(available.get(0)), whatsapp), ok, unexpected);
        Callable<Void> attempt2 = () -> runGetOrCreate(startGate,
                pickupOrder(List.of(available.get(1)), whatsapp), ok, unexpected);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> f1 = pool.submit(attempt1);
        Future<?> f2 = pool.submit(attempt2);
        startGate.countDown();
        f1.get(30, TimeUnit.SECONDS);
        f2.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(ok.get()).isEqualTo(2);
        assertThat(unexpected.get()).isZero();
        // the UNIQUE constraint + insert-or-select collapse the race into one row
        assertThat(customerRepository.count()).isEqualTo(customersBefore + 1);
        assertThat(customerRepository.findByWhatsapp(whatsapp)).isPresent();
    }

    private Void runOne(CountDownLatch gate, CreateOrderRequest request,
                        AtomicInteger wins, AtomicInteger conflicts) throws InterruptedException {
        gate.await();
        try {
            orderService.createOrder(request);
            wins.incrementAndGet();
        } catch (ProductNotAvailableException _) {
            conflicts.incrementAndGet();
        }
        return null;
    }

    private Void runGetOrCreate(CountDownLatch gate, CreateOrderRequest request,
                                AtomicInteger ok, AtomicInteger unexpected) throws InterruptedException {
        gate.await();
        try {
            orderService.createOrder(request);
            ok.incrementAndGet();
        } catch (RuntimeException _) {
            unexpected.incrementAndGet();
        }
        return null;
    }
}
