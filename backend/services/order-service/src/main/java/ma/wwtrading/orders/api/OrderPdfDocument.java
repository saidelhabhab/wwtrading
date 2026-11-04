package ma.wwtrading.orders.api;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OrderPdfDocument {
    private static final Color MAGENTA = new Color(167, 14, 75);
    private static final Color INK = new Color(29, 29, 31);
    private static final Color MUTED = new Color(92, 92, 96);
    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    public byte[] render(OrderResponse order) {
        try (PDDocument document = new PDDocument(); Canvas canvas = new Canvas(document)) {
            canvas.title("BON DE COMMANDE", "CMD-" + order.id());
            canvas.label("Date", order.createdAt() == null ? null
                    : DateTimeFormatter.ofPattern("dd/MM/yyyy").format(
                            order.createdAt().atZone(ZoneId.systemDefault())));
            canvas.label("Statut", order.status() == null ? null : order.status().name());
            if (order.quotationId() != null) canvas.label("Devis source", "DEV-" + order.quotationId());

            canvas.section("CLIENT");
            canvas.label("Compte", order.customerReference());
            ShippingAddressSnapshot address = order.shippingAddress();
            if (address != null) {
                canvas.label("Nom", (address.getFirstName() == null ? "" : address.getFirstName() + " ")
                        + (address.getLastName() == null ? "" : address.getLastName()));
                canvas.label("Société", address.getCompany());
                canvas.label("Téléphone", address.getPhone());
            }

            canvas.section("LIVRAISON ET PAIEMENT");
            canvas.label("Mode", order.deliveryMethod() == ma.wwtrading.orders.domain.DeliveryMethod.PICKUP
                    ? "Retrait sur place" : "Livraison");
            if (order.deliveryArea() != null && order.deliveryMethod() == ma.wwtrading.orders.domain.DeliveryMethod.DELIVERY) {
                canvas.label("Zone", deliveryAreaLabel(order.deliveryArea()));
            }
            if (address != null) {
                canvas.label("Adresse", address.getAddressLine1());
                canvas.label("Complément", address.getAddressLine2());
                canvas.label("Ville", (address.getPostalCode() == null ? "" : address.getPostalCode() + " ")
                        + (address.getCity() == null ? "" : address.getCity()));
                canvas.label("Pays", address.getCountry());
            }
            canvas.label("Modalité de paiement", paymentMethodLabel(order));
            canvas.label("État du paiement", paymentStatusLabel(order.paymentStatus()));

            canvas.section("ARTICLES COMMANDÉS");
            canvas.tableHeader();
            for (OrderResponse.OrderItemResponse item : order.items()) {
                canvas.row(item.productReference(), item.quantity(), item.unitPrice(), item.subtotal());
            }
            canvas.section("MONTANTS");
            canvas.amount("Sous-total", order.subtotal(), false);
            if (order.discount() != null && order.discount().signum() > 0) {
                canvas.amount("Remise du devis", order.discount().negate(), false);
            }
            canvas.amount(deliveryFeeLabel(order), order.deliveryFee(), false);
            canvas.amount("TOTAL DE LA COMMANDE", order.total(), true);
            if (order.paymentPlan() == ma.wwtrading.orders.domain.PaymentPlan.SPLIT_50_50
                    || order.paymentMethod() == ma.wwtrading.orders.domain.PaymentMethod.SPLIT_50_50) {
                canvas.amount("Acompte initial (50 % virement)", order.upfrontPaymentAmount(), false);
                canvas.amount("Solde à la livraison (50 %)", order.remainingDeliveryAmount(), false);
            }
            canvas.amount("Montant payé", order.amountPaid(), false);
            canvas.amount("Reste à payer", order.outstandingBalance(), false);
            return canvas.bytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "The document could not be generated");
        }
    }

    private static String paymentMethodLabel(OrderResponse order) {
        if (order.paymentPlan() == ma.wwtrading.orders.domain.PaymentPlan.SPLIT_50_50
                || order.paymentMethod() == ma.wwtrading.orders.domain.PaymentMethod.SPLIT_50_50) {
            return "50 % par virement à la commande + 50 % à la livraison";
        }
        if (order.paymentMethod() == ma.wwtrading.orders.domain.PaymentMethod.BANK_TRANSFER) {
            return "Virement bancaire intégral (100 %)";
        }
        return order.deliveryMethod() == ma.wwtrading.orders.domain.DeliveryMethod.PICKUP
                ? "Paiement sur place" : "Paiement à la livraison";
    }

    private static String paymentStatusLabel(ma.wwtrading.orders.domain.PaymentStatus status) {
        if (status == null) return "";
        return switch (status) {
            case UNPAID -> "Non payé (UNPAID)";
            case PARTIALLY_PAID -> "Partiellement payé — Acompte 50 % reçu (PARTIALLY_PAID)";
            case PAID -> "Payé intégralement (PAID)";
        };
    }

    private static String deliveryAreaLabel(String area) {
        return switch (area == null ? "" : area) {
            case "KENITRA_AREA" -> "Kénitra et environs";
            case "CASABLANCA" -> "Casablanca";
            case "OTHER" -> "Autres villes et régions";
            default -> area;
        };
    }

    private static String deliveryFeeLabel(OrderResponse order) {
        if ("ADMIN_DISABLED".equals(order.deliveryFeeWaivedReason())) {
            return "Livraison (frais désactivés)";
        }
        if ("FREE_THRESHOLD".equals(order.deliveryFeeWaivedReason())) {
            return "Livraison (offerte > 20 000 DH)";
        }
        return "Livraison";
    }

    private static String money(BigDecimal value) {
        if (value == null) return "-";
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.FRANCE))
                .format(value).replace('\u202f', ' ').replace('\u00a0', ' ') + " DH";
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.replace('\u2019', '\'').replace('\u2013', '-').replace('\u2014', '-')
                .replace('\u00a0', ' ').replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]", "?");
    }

    private static final class Canvas implements AutoCloseable {
        private final PDDocument document;
        private PDPageContentStream stream;
        private float y;
        private int page;

        Canvas(PDDocument document) throws IOException {
            this.document = document;
            nextPage();
        }

        void title(String title, String reference) throws IOException {
            text("WWTRADING", 48, y, BOLD, 19, MAGENTA);
            text("Expertise Konica Minolta & Develop", 48, y - 17, REGULAR, 9, MUTED);
            y -= 62;
            text(title, 48, y, BOLD, 18, INK);
            y -= 22;
            text(reference, 48, y, REGULAR, 9, MUTED);
            y -= 20;
            rule(MAGENTA, 2);
            y -= 20;
        }

        void section(String heading) throws IOException {
            ensure(48);
            y -= 14;
            text(heading, 48, y, BOLD, 10, MAGENTA);
            y -= 10;
            rule(new Color(221, 221, 221), .7f);
            y -= 19;
        }

        void label(String label, String value) throws IOException {
            if (value == null || value.isBlank()) return;
            String remaining = safe(value);
            while (!remaining.isEmpty()) {
                ensure(17);
                int end = remaining.length();
                while (end > 1 && REGULAR.getStringWidth(remaining.substring(0, end)) * 9 / 1000 > 350) end--;
                text(label, 48, y, BOLD, 9, MUTED);
                text(remaining.substring(0, end), 185, y, REGULAR, 9, INK);
                remaining = remaining.substring(end).stripLeading();
                label = "";
                y -= 16;
            }
        }

        void tableHeader() throws IOException {
            ensure(35);
            stream.setNonStrokingColor(new Color(244, 239, 241));
            stream.addRect(48, y - 9, 498, 25);
            stream.fill();
            text("RÉFÉRENCE PRODUIT", 54, y, BOLD, 8, INK);
            text("QTÉ", 342, y, BOLD, 8, INK);
            text("PRIX UNIT.", 389, y, BOLD, 8, INK);
            text("TOTAL", 489, y, BOLD, 8, INK);
            y -= 30;
        }

        void row(String reference, int quantity, BigDecimal unit, BigDecimal total) throws IOException {
            ensure(32);
            text(fit(reference, REGULAR, 9, 275), 54, y, REGULAR, 9, INK);
            text(Integer.toString(quantity), 342, y, REGULAR, 9, INK);
            textRight(money(unit), 472, y, REGULAR, 9, INK);
            textRight(money(total), 546, y, BOLD, 9, INK);
            y -= 25;
            rule(new Color(231, 231, 231), .5f);
            y -= 7;
        }

        void amount(String label, BigDecimal value, boolean prominent) throws IOException {
            ensure(26);
            if (prominent) { rule(MAGENTA, 1.2f); y -= 17; }
            text(label, 300, y, prominent ? BOLD : REGULAR, prominent ? 11 : 9, INK);
            textRight(money(value), 546, y, BOLD, prominent ? 11 : 9,
                    prominent ? MAGENTA : INK);
            y -= prominent ? 25 : 19;
        }

        private String fit(String value, PDFont font, float size, float max) throws IOException {
            String printable = safe(value);
            while (!printable.isEmpty() && font.getStringWidth(printable) * size / 1000 > max) {
                printable = printable.substring(0, printable.length() - 1);
            }
            return printable.equals(safe(value)) ? printable : printable + "...";
        }

        private void ensure(float height) throws IOException {
            if (y - height < 70) nextPage();
        }

        private void nextPage() throws IOException {
            if (stream != null) { footer(); stream.close(); }
            document.addPage(new PDPage(PDRectangle.A4));
            stream = new PDPageContentStream(document, document.getPage(document.getNumberOfPages() - 1));
            page++;
            y = 780;
        }

        private void footer() throws IOException {
            stream.setStrokingColor(new Color(220, 220, 220));
            stream.moveTo(48, 55);
            stream.lineTo(546, 55);
            stream.stroke();
            text("WWTRADING  |  Document commercial", 48, 40, REGULAR, 8, MUTED);
            textRight(Integer.toString(page), 546, 40, REGULAR, 8, MUTED);
        }

        private void rule(Color color, float width) throws IOException {
            stream.setStrokingColor(color);
            stream.setLineWidth(width);
            stream.moveTo(48, y);
            stream.lineTo(546, y);
            stream.stroke();
        }

        private void textRight(String value, float right, float baseline, PDFont font,
                float size, Color color) throws IOException {
            String printable = safe(value);
            text(printable, right - font.getStringWidth(printable) * size / 1000, baseline, font, size, color);
        }

        private void text(String value, float x, float baseline, PDFont font,
                float size, Color color) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(color);
            stream.newLineAtOffset(x, baseline);
            stream.showText(safe(value));
            stream.endText();
        }

        byte[] bytes() throws IOException {
            footer();
            stream.close();
            stream = null;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }

        @Override public void close() throws IOException {
            if (stream != null) { footer(); stream.close(); }
        }
    }
}
