package com.secondlap.backend.customer;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerCreator customerCreator;

    public CustomerService(CustomerRepository customerRepository, CustomerCreator customerCreator) {
        this.customerRepository = customerRepository;
        this.customerCreator = customerCreator;
    }

    /**
     * Resolve the customer for an order by WhatsApp number, creating one if it is
     * new. The UNIQUE constraint on whatsapp — not this code — is what guarantees
     * a single customer per number: two concurrent orders from the same new number
     * race on the INSERT, one wins and the loser's constraint violation falls back
     * to re-selecting the just-created row. The INSERT runs in a REQUIRES_NEW
     * sub-transaction (see CustomerCreator) so a losing race rolls back only that
     * nested transaction, never the caller's order transaction.
     */
    public Customer getOrCreate(String rawWhatsapp, String name, String comments) {
        String whatsapp = WhatsappNormalizer.normalize(rawWhatsapp);

        return customerRepository.findByWhatsapp(whatsapp)
                .map(existing -> updateNameIfChanged(existing, name))
                .orElseGet(() -> insertOrReselect(whatsapp, name, comments));
    }

    private Customer insertOrReselect(String whatsapp, String name, String comments) {
        try {
            Customer created = customerCreator.insert(name, whatsapp, comments);
            // Re-attach to the caller's persistence context for use in this order.
            return customerRepository.findByWhatsapp(whatsapp).orElse(created);
        } catch (DataIntegrityViolationException concurrentInsert) {
            return customerRepository.findByWhatsapp(whatsapp)
                    .map(existing -> updateNameIfChanged(existing, name))
                    .orElseThrow(() -> concurrentInsert);
        }
    }

    private Customer updateNameIfChanged(Customer customer, String name) {
        if (name != null && !name.equals(customer.getName())) {
            customer.setName(name);
        }
        return customer;
    }
}
