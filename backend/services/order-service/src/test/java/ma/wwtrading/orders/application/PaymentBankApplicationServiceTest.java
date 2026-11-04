package ma.wwtrading.orders.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ma.wwtrading.orders.api.CreatePaymentBankRequest;
import ma.wwtrading.orders.api.PaymentBankResponse;
import ma.wwtrading.orders.api.UpdatePaymentBankRequest;
import ma.wwtrading.orders.domain.PaymentBankEntity;
import ma.wwtrading.orders.domain.PaymentBankRepository;
import ma.wwtrading.orders.infrastructure.PaymentBankLogoStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentBankApplicationServiceTest {

    private PaymentBankRepository repository;
    private PaymentBankLogoStorage logoStorage;
    private PaymentBankApplicationService service;

    @BeforeEach
    void setUp() {
        repository = mock(PaymentBankRepository.class);
        logoStorage = mock(PaymentBankLogoStorage.class);
        service = new PaymentBankApplicationService(repository, logoStorage);
    }

    @Test
    void createBank_Success() {
        CreatePaymentBankRequest request = new CreatePaymentBankRequest(
                "ATW-01", "Attijariwafa Bank", "MA640001111122223333444455", "WWTRADING SARL", "https://example.com/logo.png", true
        );

        when(repository.existsByReference("ATW-01")).thenReturn(false);
        when(repository.save(any(PaymentBankEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentBankResponse response = service.createBank(request);

        assertThat(response.reference()).isEqualTo("ATW-01");
        assertThat(response.name()).isEqualTo("Attijariwafa Bank");
        assertThat(response.rib()).isEqualTo("MA640001111122223333444455");
        assertThat(response.beneficiary()).isEqualTo("WWTRADING SARL");
        assertThat(response.logoUrl()).isEqualTo("https://example.com/logo.png");
        assertThat(response.active()).isTrue();
    }

    @Test
    void createBank_DuplicateReference_ThrowsConflict() {
        CreatePaymentBankRequest request = new CreatePaymentBankRequest(
                "ATW-01", "Attijariwafa Bank", "MA640001111122223333444455", "WWTRADING SARL", null, true
        );

        when(repository.existsByReference("ATW-01")).thenReturn(true);

        assertThatThrownBy(() -> service.createBank(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateBank_Success() {
        UUID bankId = UUID.randomUUID();
        PaymentBankEntity existing = new PaymentBankEntity("ATW-01", "Old Name", "RIB1", "Ben1", null, true);

        UpdatePaymentBankRequest request = new UpdatePaymentBankRequest(
                "ATW-01-UPDATED", "Attijari Bank Updated", "MA640001111122223333444499", "WWTRADING MAROC", "https://example.com/logo2.png", true
        );

        when(repository.findById(bankId)).thenReturn(Optional.of(existing));
        when(repository.existsByReferenceAndIdNot("ATW-01-UPDATED", bankId)).thenReturn(false);
        when(repository.save(any(PaymentBankEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentBankResponse response = service.updateBank(bankId, request);

        assertThat(response.reference()).isEqualTo("ATW-01-UPDATED");
        assertThat(response.name()).isEqualTo("Attijari Bank Updated");
        assertThat(response.rib()).isEqualTo("MA640001111122223333444499");
    }

    @Test
    void setActive_TogglesActiveState() {
        UUID bankId = UUID.randomUUID();
        PaymentBankEntity existing = new PaymentBankEntity("ATW-01", "Attijari", "RIB1", "Ben1", null, true);

        when(repository.findById(bankId)).thenReturn(Optional.of(existing));
        when(repository.save(any(PaymentBankEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentBankResponse response = service.setActive(bankId, false);

        assertThat(response.active()).isFalse();
    }

    @Test
    void listActiveBanks_ReturnsOnlyActive() {
        PaymentBankEntity bank1 = new PaymentBankEntity("ATW-01", "Attijari", "RIB1", "Ben1", null, true);
        when(repository.findByActiveTrueOrderByCreatedAtDesc()).thenReturn(List.of(bank1));

        List<PaymentBankResponse> list = service.listActiveBanks();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).reference()).isEqualTo("ATW-01");
    }
}

