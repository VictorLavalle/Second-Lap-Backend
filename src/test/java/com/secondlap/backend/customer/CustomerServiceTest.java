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

    private final CustomerRepository repo = mock(CustomerRepository.class);
    private final CustomerCreator creator = mock(CustomerCreator.class);
    private final CustomerService service = new CustomerService(repo, creator);

    @Test
    void createsCustomerWhenWhatsappIsNew() {
        Customer created = new Customer("Ana", "9991234567", null);
        when(repo.findByWhatsapp("9991234567")).thenReturn(Optional.empty(), Optional.of(created));
        when(creator.insert("Ana", "9991234567", null)).thenReturn(created);

        Customer result = service.getOrCreate("(999) 123-4567", "Ana", null);

        assertThat(result.getName()).isEqualTo("Ana");
        assertThat(result.getWhatsapp()).isEqualTo("9991234567");
    }

    @Test
    void reusesExistingCustomerAndUpdatesName() {
        Customer existing = new Customer("Old", "9991234567", null);
        when(repo.findByWhatsapp("9991234567")).thenReturn(Optional.of(existing));

        Customer result = service.getOrCreate("9991234567", "New", null);

        assertThat(result).isSameAs(existing);
        assertThat(result.getName()).isEqualTo("New");
    }

    @Test
    void keepsNameWhenUnchangedOrNull() {
        Customer existing = new Customer("Same", "9991234567", null);
        when(repo.findByWhatsapp("9991234567")).thenReturn(Optional.of(existing));

        assertThat(service.getOrCreate("9991234567", "Same", null).getName()).isEqualTo("Same");
        assertThat(service.getOrCreate("9991234567", null, null).getName()).isEqualTo("Same");
    }

    @Test
    void fallsBackToSelectWhenConcurrentInsertViolatesUnique() {
        Customer winner = new Customer("Winner", "9991234567", null);
        // absent on first check -> insert loses the race -> re-select finds the winner
        when(repo.findByWhatsapp("9991234567"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(creator.insert(any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate whatsapp"));

        Customer result = service.getOrCreate("9991234567", "Loser", null);

        assertThat(result).isSameAs(winner);
    }

    @Test
    void rethrowsWhenInsertFailsAndRowStillAbsent() {
        when(repo.findByWhatsapp("9991234567")).thenReturn(Optional.empty());
        when(creator.insert(any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> service.getOrCreate("9991234567", "X", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
