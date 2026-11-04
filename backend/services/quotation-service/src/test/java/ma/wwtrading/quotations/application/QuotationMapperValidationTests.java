package ma.wwtrading.quotations.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import ma.wwtrading.quotations.api.CreateQuotationRequest;
import ma.wwtrading.quotations.domain.QuotationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;

class QuotationMapperValidationTests {

    private final QuotationMapper mapper = Mappers.getMapper(QuotationMapper.class);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void mapsClientFieldsAndAssignsServerManagedStatus() {
        var request = new CreateQuotationRequest(
            "VENTE",
            "Bizhub C250i",
            "Konica Minolta",
            1,
            "Maintenance request",
            "Jean Dupont",
            "ACME",
            "0612345678",
            "user-123@example.com",
            "Kénitra",
            "ASAP",
            new BigDecimal("10000.00"),
            "Please contact customer"
        );

        var quotation = mapper.toEntity("user-123", request);
        var response = mapper.toResponse(quotation);

        assertThat(quotation.getStatus()).isEqualTo(QuotationStatus.PENDING);
        assertThat(response.customerReference()).isEqualTo("user-123");
        assertThat(response.serviceType()).isEqualTo("VENTE");
        assertThat(response.status()).isEqualTo(QuotationStatus.PENDING);
    }

    @Test
    void rejectsBlankRequiredFieldsAndOversizedNotes() {
        var violations = validator.validate(
            new CreateQuotationRequest(
                " ",
                "",
                " ",
                0,
                " ",
                " ",
                "",
                " ",
                "not-an-email",
                " ",
                "",
                new BigDecimal("-1.00"),
                "x".repeat(4001)
            )
        );

        assertThat(violations)
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("serviceType", "quantity", "fullName", "phone", "email", "city");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "0612345678",
        "0700123456",
        "0522123456",
        "+212612345678",
        "+212 6 12 34 56 78",
        "+212 522-123456",
        "+33 1 42 68 00 00",
        "+1 (555) 123-4567"
    })
    @DisplayName("Accepts valid Moroccan and international phone numbers")
    void acceptsValidPhoneNumbers(String validPhone) {
        var request = new CreateQuotationRequest(
            "VENTE",
            null,
            null,
            1,
            "Description",
            "Karim Alami",
            "Tech SA",
            validPhone,
            "contact@tech.ma",
            "Casablanca",
            null,
            null,
            null
        );

        var violations = validator.validate(request);
        assertThat(violations)
            .filteredOn(v -> v.getPropertyPath().toString().equals("phone"))
            .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "abc-non-tel",
        "1234",
        "phone123456",
        "06-abc-1234",
        "++212612345678"
    })
    @DisplayName("Rejects invalid phone numbers containing letters or invalid formats")
    void rejectsInvalidPhoneNumbers(String invalidPhone) {
        var request = new CreateQuotationRequest(
            "VENTE",
            null,
            null,
            1,
            "Description",
            "Karim Alami",
            "Tech SA",
            invalidPhone,
            "contact@tech.ma",
            "Casablanca",
            null,
            null,
            null
        );

        var violations = validator.validate(request);
        assertThat(violations)
            .extracting(v -> v.getPropertyPath().toString())
            .contains("phone");
    }

    @Test
    @DisplayName("Rejects quantities exceeding 9999 units")
    void rejectsExcessiveQuantities() {
        var request = new CreateQuotationRequest(
            "VENTE",
            null,
            null,
            10000,
            "Description",
            "Karim Alami",
            "Tech SA",
            "0612345678",
            "contact@tech.ma",
            "Casablanca",
            null,
            null,
            null,
            List.of(new CreateQuotationRequest.RequestedLine(UUID.randomUUID(), 10000))
        );

        var violations = validator.validate(request);
        assertThat(violations)
            .extracting(v -> v.getPropertyPath().toString())
            .contains("quantity", "requestedLines[0].quantity");
    }
}
