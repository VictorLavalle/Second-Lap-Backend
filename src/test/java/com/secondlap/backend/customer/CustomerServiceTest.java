package com.secondlap.backend.customer;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerServiceTest {

    @Test
    void createsCustomerWhenWhatsappIsNew() {
        CustomerRepository repo = mock(CustomerRepository.class);
        when(repo.findByWhatsapp("9991234567")).thenReturn(Optional.empty());
        when(repo.saveAndFlush(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        Customer result = new CustomerService(repo).getOrCreate("(999) 123-4567", "Ana", null);

        assertThat(result.getName()).isEqualTo("Ana");
        assertThat(result.getWhatsapp()).isEqualTo("9991234567");
    }

    @Test
    void reusesExistingCustomerAndUpdatesName() {
        CustomerRepository repo = mock(CustomerRepository.class);
        Customer existing = new Customer("Old", "9991234567", null);
        when(repo.findByWhatsapp("9991234567")).thenReturn(Optional.of(existing));

        Customer result = new CustomerService(repo).getOrCreate("9991234567", "New", null);

        assertThat(result).isSameAs(existing);
        assertThat(result.getName()).isEqualTo("New");
    }

    @Test
    void keepsNameWhenUnchangedOrNull() {
        CustomerRepository repo = mock(CustomerRepository.class);
        Customer existing = new Customer("Same", "9991234567", null);
        when(repo.findByWhatsapp("9991234567")).thenReturn(Optional.of(existing));
        CustomerService service = new CustomerService(repo);

        assertThat(service.getOrCreate("9991234567", "Same", null).getName()).isEqualTo("Same");
        assertThat(service.getOrCreate("9991234567", null, null).getName()).isEqualTo("Same");
    }

    @Test
    void fallsBackToSelectWhenConcurrentInsertViolatesUnique() {
        CustomerRepository repo = mock(CustomerRepository.class);
        Customer winner = new Customer("Winner", "9991234567", null);
        // first lookup: absent -> try insert; insert loses the race; re-select wins
        when(repo.findByWhatsapp("9991234567"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(repo.saveAndFlush(any(Customer.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate whatsapp"));

        Customer result = new CustomerService(repo).getOrCreate("9991234567", "Loser", null);

        assertThat(result).isSameAs(winner);
    }

    @Test
    void rethrowsWhenInsertFailsAndRowStillAbsent() {
        CustomerRepository repo = mock(CustomerRepository.class);
        when(repo.findByWhatsapp("9991234567")).thenReturn(Optional.empty());
        when(repo.saveAndFlush(any(Customer.class)))
                .thenThrow(new DataIntegrityViolationException("boom"));

        CustomerService service = new CustomerService(repo);
        assertThatThrownBy(() -> service.getOrCreate("9991234567", "X", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
