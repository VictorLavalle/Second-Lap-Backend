package com.secondlap.backend.order;

import com.secondlap.backend.common.ProductNotAvailableException;
import com.secondlap.backend.common.RateLimitExceededException;
import com.secondlap.backend.common.ResourceNotFoundException;
import com.secondlap.backend.config.StoreProperties;
import com.secondlap.backend.customer.Customer;
import com.secondlap.backend.customer.CustomerService;
import com.secondlap.backend.customer.WhatsappNormalizer;
import com.secondlap.backend.neighborhood.Neighborhood;
import com.secondlap.backend.neighborhood.NeighborhoodRepository;
import com.secondlap.backend.order.dto.CreateOrderRequest;
import com.secondlap.backend.order.dto.OrderResponse;
import com.secondlap.backend.order.dto.ShippingAddressRequest;
import com.secondlap.backend.product.Product;
import com.secondlap.backend.product.ProductRepository;
import com.secondlap.backend.ratelimit.RateLimitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final NeighborhoodRepository neighborhoodRepository;
    private final CustomerService customerService;
    private final OrderMapper orderMapper;
    private final StoreProperties storeProperties;
    private final OrderProperties orderProperties;
    private final RateLimitService rateLimitService;

    public OrderService(ProductRepository productRepository,
                        OrderRepository orderRepository,
                        NeighborhoodRepository neighborhoodRepository,
                        CustomerService customerService,
                        OrderMapper orderMapper,
                        StoreProperties storeProperties,
                        OrderProperties orderProperties,
                        RateLimitService rateLimitService) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.neighborhoodRepository = neighborhoodRepository;
        this.customerService = customerService;
        this.orderMapper = orderMapper;
        this.storeProperties = storeProperties;
        this.orderProperties = orderProperties;
        this.rateLimitService = rateLimitService;
    }

    /**
     * Reserve all requested items and create the order in a single transaction.
     *
     * <p>Concurrency guarantee: a unique piece can never be reserved by two
     * clients at once. This is enforced by the atomic conditional UPDATE
     * ({@code SET status='apartado' WHERE id=? AND status='disponible'}) plus
     * PostgreSQL's row lock — NOT by application code. Under READ COMMITTED, a
     * concurrent reservation blocks on the locked row and re-evaluates the WHERE
     * predicate after the winner commits, so it matches zero rows and loses with
     * a 409. READ COMMITTED (PostgreSQL's default) is sufficient precisely because
     * of this re-check-after-lock behavior; it is pinned explicitly here so the
     * guarantee does not silently depend on the environment default.
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public OrderResponse createOrder(CreateOrderRequest request) {
        DeliveryMethod deliveryMethod = DeliveryMethod.fromValue(request.deliveryMethod());
        PaymentMethod paymentMethod = PaymentMethod.fromValue(request.paymentMethod());
        validateAddressPresence(deliveryMethod, request.address());
        validateItemCount(request.productIds());

        // Throttle per WhatsApp before touching inventory: a single number must
        // not drain the unique-piece catalogue by scripting many reservations.
        String whatsapp = WhatsappNormalizer.normalize(request.customer().whatsapp());
        if (!rateLimitService.tryConsumeOrderByWhatsapp(whatsapp)) {
            throw new RateLimitExceededException("Demasiados pedidos para este número, intenta más tarde");
        }

        List<Product> products = loadProductsForReservation(request.productIds());
        reserveAllOrFail(products);

        Customer customer = customerService.getOrCreate(
                whatsapp,
                request.customer().name(),
                request.customer().comments());

        Address address = buildAddress(deliveryMethod, request.address());
        Order order = new Order(customer, address, deliveryMethod, paymentMethod);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (Product product : products) {
            order.addItem(new OrderItem(order, product, product.getPrice()));
            subtotal = subtotal.add(product.getPrice());
        }

        BigDecimal shippingFee = address != null && address.getShippingFee() != null
                ? address.getShippingFee()
                : BigDecimal.ZERO;
        order.applyTotals(subtotal, shippingFee, subtotal.add(shippingFee));
        order.recordEvent(null, OrderStatus.PENDING);

        Order saved = orderRepository.save(order);
        return orderMapper.toResponse(saved, storeProperties.whatsapp());
    }

    private List<Product> loadProductsForReservation(List<UUID> productIds) {
        List<Product> products = productRepository.findByPublicIdInOrderByIdAsc(productIds);
        if (products.size() != productIds.size()) {
            Map<UUID, Product> byPublicId = products.stream()
                    .collect(Collectors.toMap(Product::getPublicId, Function.identity()));
            UUID missing = productIds.stream()
                    .filter(id -> !byPublicId.containsKey(id))
                    .findFirst()
                    .orElseThrow();
            throw new ResourceNotFoundException("Product not found: " + missing);
        }
        return products;
    }

    /**
     * Reserve every product atomically. Products are locked in ascending id order
     * (loadProductsForReservation sorts them) so concurrent orders with overlapping
     * items acquire locks in the same sequence and cannot deadlock. If any product
     * is no longer available the conditional UPDATE affects zero rows and the whole
     * transaction rolls back, releasing anything already reserved.
     */
    private void reserveAllOrFail(List<Product> products) {
        for (Product product : products) {
            int reserved = productRepository.reserveIfAvailable(product.getId());
            if (reserved == 0) {
                throw new ProductNotAvailableException(product.getPublicId().toString());
            }
        }
    }

    private void validateItemCount(List<UUID> productIds) {
        if (productIds.size() > orderProperties.maxItems()) {
            throw new IllegalArgumentException(
                    "an order cannot contain more than " + orderProperties.maxItems() + " items");
        }
    }

    private void validateAddressPresence(DeliveryMethod deliveryMethod, ShippingAddressRequest address) {
        if (deliveryMethod.requiresAddress() && address == null) {
            throw new IllegalArgumentException(
                    "address is required for delivery method " + deliveryMethod.getValue());
        }
        if (!deliveryMethod.requiresAddress() && address != null) {
            throw new IllegalArgumentException(
                    "address must be omitted for delivery method " + deliveryMethod.getValue());
        }
    }

    private Address buildAddress(DeliveryMethod deliveryMethod, ShippingAddressRequest request) {
        if (!deliveryMethod.requiresAddress()) {
            return null;
        }
        Address address = new Address();
        address.setStreet(request.street());
        address.setBetweenStreets(request.betweenStreets());
        address.setNeighborhood(request.neighborhood());
        address.setCity(request.city());
        address.setState(request.state());
        address.setPostalCode(request.postalCode());
        address.setReferenceNotes(request.references());
        address.setMapsLink(request.mapsLink());
        address.setShippingFee(resolveShippingFee(request.neighborhood()));
        return address;
    }

    private BigDecimal resolveShippingFee(String neighborhoodName) {
        return neighborhoodRepository.findByName(neighborhoodName)
                .map(Neighborhood::getFee)
                .orElse(null);
    }
}
