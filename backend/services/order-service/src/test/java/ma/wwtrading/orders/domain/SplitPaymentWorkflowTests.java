package ma.wwtrading.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import ma.wwtrading.orders.api.ShippingAddressSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SplitPaymentWorkflowTests {

    private OrderEntity createSplitOrder(DeliveryMethod deliveryMethod) {
        ShippingAddressSnapshot address = new ShippingAddressSnapshot(
                "Hamza", "Karout", "WWTRADING", "+212678745490",
                "Hay Saada", "Beni Makada", "Tanger", "13489", "Maroc");

        OrderItemEntity item = new OrderItemEntity("PROD-KM-650I", "Konica Minolta bizhub 650i", 1, new BigDecimal("9600.00"));
        BigDecimal deliveryFee = new BigDecimal("400.00");

        OrderEntity order = new OrderEntity(
                "customer@example.test",
                OrderStatus.DRAFT,
                address,
                deliveryMethod,
                PaymentMethod.SPLIT_50_50,
                List.of(item),
                deliveryFee,
                null,
                BigDecimal.ZERO.setScale(2),
                deliveryMethod == DeliveryMethod.PICKUP ? "PICKUP" : "Tanger",
                deliveryMethod == DeliveryMethod.PICKUP ? "PICKUP" : null);

        order.selectBank("bank-cih");
        return order;
    }

    @Test
    @DisplayName("1. Total, acompte et solde initialement calculés côté serveur")
    void initialSplitPaymentFinancialCalculations() {
        OrderEntity order = createSplitOrder(DeliveryMethod.DELIVERY);

        assertThat(order.getTotal()).isEqualByComparingTo("10000.00");
        assertThat(order.getUpfrontPaymentAmount()).isEqualByComparingTo("5000.00");
        assertThat(order.getRemainingDeliveryAmount()).isEqualByComparingTo("5000.00");
        assertThat(order.getAmountPaid()).isEqualByComparingTo("0.00");
        assertThat(order.getOutstandingBalance()).isEqualByComparingTo("10000.00");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    @DisplayName("2. Tant que le 1er acompte n'est pas confirmé, le 2ème versement ne peut pas être sélectionné ou confirmé")
    void cannotProceedWithSecondInstallmentBeforeFirstIsConfirmed() {
        OrderEntity order = createSplitOrder(DeliveryMethod.DELIVERY);

        // Cannot attach 2nd transfer proof
        assertThatThrownBy(() -> order.attachSecondTransferProof("proofs/second.png"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("First installment must be confirmed");

        // Cannot select second installment payment method
        assertThatThrownBy(() -> order.selectSecondInstallmentMethod("CASH"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("First installment must be confirmed");

        assertThatThrownBy(() -> order.selectSecondInstallmentMethod("BANK_TRANSFER"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("First installment must be confirmed");
    }

    @Test
    @DisplayName("3. Attachement et validation du premier acompte par virement bancaire")
    void firstInstallmentSubmissionAndConfirmation() {
        OrderEntity order = createSplitOrder(DeliveryMethod.DELIVERY);

        order.attachTransferProof("proofs/first-acompte.png");
        assertThat(order.getTransferProofReference()).isEqualTo("proofs/first-acompte.png");
        assertThat(order.getFirstTransferProofSubmittedAt()).isNotNull();

        // Admin confirms 1st installment
        order.confirmPayment(new BigDecimal("5000.00"));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(order.getAmountPaid()).isEqualByComparingTo("5000.00");
        assertThat(order.getOutstandingBalance()).isEqualByComparingTo("5000.00");
        assertThat(order.getFirstInstallmentConfirmedAt()).isNotNull();
        assertThat(order.getSecondInstallmentConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("4. Le client choisit le virement bancaire pour le 2ème versement et soumet le 2ème reçu sans écraser le 1er")
    void clientChoosesBankTransferForSecondInstallment() {
        OrderEntity order = createSplitOrder(DeliveryMethod.DELIVERY);
        order.attachTransferProof("proofs/first-acompte.png");
        order.confirmPayment(new BigDecimal("5000.00"));

        // Client chooses BANK_TRANSFER
        order.selectSecondInstallmentMethod("BANK_TRANSFER");
        assertThat(order.getSecondInstallmentPaymentMethod()).isEqualTo("BANK_TRANSFER");

        // Client uploads second proof
        order.attachSecondTransferProof("proofs/second-solde.png");
        assertThat(order.getTransferProofReference()).isEqualTo("proofs/first-acompte.png"); // 1st proof preserved!
        assertThat(order.getSecondTransferProofReference()).isEqualTo("proofs/second-solde.png");
        assertThat(order.getSecondTransferProofSubmittedAt()).isNotNull();

        // While pending validation, balance remains due
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(order.getOutstandingBalance()).isEqualByComparingTo("5000.00");

        // Admin validates 2nd installment
        order.confirmPayment(new BigDecimal("5000.00"), "staff_amine");

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getAmountPaid()).isEqualByComparingTo("10000.00");
        assertThat(order.getOutstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(order.getSecondInstallmentConfirmedAt()).isNotNull();
        assertThat(order.getSecondInstallmentConfirmedBy()).isEqualTo("staff_amine");
    }

    @Test
    @DisplayName("5. Le client choisit le paiement en espèces (CASH) pour le 2ème versement")
    void clientChoosesCashForSecondInstallment() {
        OrderEntity order = createSplitOrder(DeliveryMethod.DELIVERY);
        order.attachTransferProof("proofs/first-acompte.png");
        order.confirmPayment(new BigDecimal("5000.00"));

        // Client chooses CASH
        order.selectSecondInstallmentMethod("CASH");
        assertThat(order.getSecondInstallmentPaymentMethod()).isEqualTo("CASH");

        // Solde remains due and payment status remains PARTIALLY_PAID until cash is collected by staff
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(order.getOutstandingBalance()).isEqualByComparingTo("5000.00");
        assertThat(order.getSecondInstallmentConfirmedAt()).isNull();

        // Authorized staff confirms cash collection
        order.confirmPayment(new BigDecimal("5000.00"), "livreur_rachid");

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getAmountPaid()).isEqualByComparingTo("10000.00");
        assertThat(order.getOutstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(order.getSecondInstallmentConfirmedAt()).isNotNull();
        assertThat(order.getSecondInstallmentConfirmedBy()).isEqualTo("livreur_rachid");
    }

    @Test
    @DisplayName("6. Le mode PICKUP (retrait) fonctionne à l'identique pour les deux options")
    void pickupOrderSupportsBothOptions() {
        OrderEntity order = createSplitOrder(DeliveryMethod.PICKUP);
        order.attachTransferProof("proofs/first-acompte.png");
        order.confirmPayment(new BigDecimal("5000.00"));

        order.selectSecondInstallmentMethod("CASH");
        assertThat(order.getSecondInstallmentPaymentMethod()).isEqualTo("CASH");

        order.confirmPayment(new BigDecimal("5000.00"), "showroom_agent");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getSecondInstallmentConfirmedBy()).isEqualTo("showroom_agent");
    }

    @Test
    @DisplayName("7. Sécurité financière : impossible de surpayer ou de revalider une commande payée")
    void cannotOverpayOrDoubleConfirm() {
        OrderEntity order = createSplitOrder(DeliveryMethod.DELIVERY);
        order.confirmPayment(new BigDecimal("5000.00"));
        order.confirmPayment(new BigDecimal("5000.00"), "admin");

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        // Attempting to confirm payment again fails
        assertThatThrownBy(() -> order.confirmPayment(new BigDecimal("5000.00"), "admin"))
                .isInstanceOf(IllegalStateException.class);

        // Attempting to select method on paid order fails
        assertThatThrownBy(() -> order.selectSecondInstallmentMethod("CASH"))
                .isInstanceOf(IllegalStateException.class);
    }
}

