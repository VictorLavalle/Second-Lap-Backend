package com.secondlap.backend.customer;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserts a customer in its own transaction. Isolating the INSERT with
 * REQUIRES_NEW means a UNIQUE-violation under a concurrent insert only rolls back
 * this nested transaction, leaving the caller's order transaction alive so it can
 * fall back to re-selecting the winning row (insert-or-select). Must be a separate
 * bean: self-invocation would bypass the transactional proxy.
 */
@Component
public class CustomerCreator {

    private final CustomerRepository customerRepository;

    public CustomerCreator(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Customer insert(String name, String whatsapp, String comments) {
        return customerRepository.saveAndFlush(new Customer(name, whatsapp, comments));
    }
}
