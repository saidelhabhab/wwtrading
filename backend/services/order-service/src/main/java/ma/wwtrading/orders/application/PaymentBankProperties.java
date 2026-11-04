package ma.wwtrading.orders.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "wwtrading.payment")
public class PaymentBankProperties {

    private List<Bank> banks = new ArrayList<>();

    public List<Bank> getBanks() {
        return banks;
    }

    public void setBanks(List<Bank> banks) {
        this.banks = banks == null ? new ArrayList<>() : new ArrayList<>(banks);
    }

    public Bank findByReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        return banks.stream()
            .filter(bank -> bank.getReference() != null && bank.getReference().equals(reference))
            .findFirst()
            .orElse(null);
    }

    public static class Bank {
        private String reference;
        private String name;
        private String rib;
        private String beneficiary;
        private String logoUrl;

        public String getReference() { return reference; }
        public void setReference(String reference) { this.reference = reference; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getRib() { return rib; }
        public void setRib(String rib) { this.rib = rib; }
        public String getBeneficiary() { return beneficiary; }
        public void setBeneficiary(String beneficiary) { this.beneficiary = beneficiary; }
        public String getLogoUrl() { return logoUrl; }
        public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    }
}