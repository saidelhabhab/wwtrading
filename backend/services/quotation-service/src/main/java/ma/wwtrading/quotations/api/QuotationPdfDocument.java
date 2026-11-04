package ma.wwtrading.quotations.api;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
public class QuotationPdfDocument {
    private static final Color MAGENTA = new Color(167, 14, 75);
    private static final Color INK = new Color(29, 29, 31);
    private static final Color MUTED = new Color(92, 92, 96);
    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] render(QuotationResponse quotation) {
        try (PDDocument document = new PDDocument(); Canvas canvas = new Canvas(document)) {
            canvas.header("DEVIS COMMERCIAL", "DEV-" + quotation.id());
            canvas.label("Date du devis", quotation.createdAt() == null ? null
                    : DATE.format(quotation.createdAt().atZone(ZoneId.systemDefault())));
            canvas.label("Statut", quotation.status() == null ? null : quotation.status().name());
            canvas.label("Validité", quotation.validUntil() == null ? null : DATE.format(quotation.validUntil()));
            canvas.section("CLIENT");
            canvas.label("Nom", quotation.fullName());
            canvas.label("Société", quotation.company());
            canvas.label("Email", quotation.email());
            canvas.label("Téléphone", quotation.phone());
            canvas.label("Ville", quotation.city());

            canvas.section("PRODUITS ET PRESTATIONS");
            if (quotation.lines() != null && !quotation.lines().isEmpty()) {
                canvas.tableHeader();
                for (QuotationResponse.QuotationLineResponse line : quotation.lines()) {
                    canvas.tableRow(line.productName(), line.productReference(),
                            line.proposedQuantity() == null ? line.requestedQuantity() : line.proposedQuantity(),
                            line.proposedUnitPrice(), line.proposedLineTotal());
                }
            } else {
                canvas.paragraph(quotation.serviceType());
                canvas.paragraph(quotation.description());
            }

            canvas.section("MONTANTS");
            if (quotation.subtotal() != null) canvas.amount("Sous-total", quotation.subtotal(), false);
            if (quotation.discount() != null) canvas.amount("Remise", quotation.discount().negate(), false);
            if (quotation.deliveryFee() != null) canvas.amount("Livraison prévue au devis", quotation.deliveryFee(), false);
            if (quotation.finalTotal() != null) canvas.amount("TOTAL DU DEVIS", quotation.finalTotal(), true);
            if (quotation.finalTotal() == null && quotation.proposedAmount() != null) {
                canvas.amount("Montant proposé", quotation.proposedAmount(), true);
            }
            return canvas.bytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "The document could not be generated");
        }
    }

    private static String money(BigDecimal value) {
        if (value == null) return "-";
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.FRANCE))
                .format(value).replace('\u202f', ' ').replace('\u00a0', ' ') + " DH";
    }

    private static String safe(String text) {
        if (text == null) return "";
        return text.replace('\u2019', '\'').replace('\u2013', '-').replace('\u2014', '-')
                .replace('\u00a0', ' ').replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]", "?");
    }

    private static final class Canvas implements AutoCloseable {
        private final PDDocument document;
        private PDPageContentStream stream;
        private float y;
        private int pageNumber;

        Canvas(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        void header(String title, String reference) throws IOException {
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
            rule(new Color(221, 221, 221), 0.7f);
            y -= 19;
        }

        void label(String label, String value) throws IOException {
            if (value == null || value.isBlank()) return;
            for (String part : wrap(value, REGULAR, 9, 345)) {
                ensure(17);
                text(label, 48, y, BOLD, 9, MUTED);
                text(part, 185, y, REGULAR, 9, INK);
                y -= 16;
                label = "";
            }
        }

        void tableHeader() throws IOException {
            ensure(35);
            stream.setNonStrokingColor(new Color(244, 239, 241));
            stream.addRect(48, y - 9, 498, 25);
            stream.fill();
            text("PRODUIT / RÉFÉRENCE", 54, y, BOLD, 8, INK);
            text("QTÉ", 342, y, BOLD, 8, INK);
            text("PRIX UNIT.", 389, y, BOLD, 8, INK);
            text("TOTAL", 489, y, BOLD, 8, INK);
            y -= 30;
        }

        void tableRow(String name, String reference, int quantity, BigDecimal unitPrice,
                BigDecimal lineTotal) throws IOException {
            ensure(44);
            text(fit(name == null ? reference : name, REGULAR, 9, 275), 54, y, REGULAR, 9, INK);
            text(fit(reference, REGULAR, 8, 275), 54, y - 13, REGULAR, 8, MUTED);
            text(Integer.toString(quantity), 342, y, REGULAR, 9, INK);
            textRight(money(unitPrice), 472, y, REGULAR, 9, INK);
            textRight(money(lineTotal), 546, y, BOLD, 9, INK);
            y -= 35;
            rule(new Color(231, 231, 231), 0.5f);
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

        void paragraph(String value) throws IOException {
            if (value == null || value.isBlank()) return;
            for (String line : wrap(value, REGULAR, 9, 490)) {
                ensure(16);
                text(line, 48, y, REGULAR, 9, INK);
                y -= 15;
            }
        }

        private void ensure(float height) throws IOException {
            if (y - height < 70) newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) { footer(); stream.close(); }
            document.addPage(new PDPage(PDRectangle.A4));
            stream = new PDPageContentStream(document, document.getPage(document.getNumberOfPages() - 1));
            pageNumber++;
            y = 780;
        }

        private void footer() throws IOException {
            stream.setStrokingColor(new Color(220, 220, 220));
            stream.moveTo(48, 55);
            stream.lineTo(546, 55);
            stream.stroke();
            text("WWTRADING  |  Document commercial", 48, 40, REGULAR, 8, MUTED);
            textRight(Integer.toString(pageNumber), 546, 40, REGULAR, 8, MUTED);
        }

        private void rule(Color color, float width) throws IOException {
            stream.setStrokingColor(color);
            stream.setLineWidth(width);
            stream.moveTo(48, y);
            stream.lineTo(546, y);
            stream.stroke();
        }

        private void text(String value, float x, float baseline, PDFont font, float size,
                Color color) throws IOException {
            stream.beginText();
            stream.setNonStrokingColor(color);
            stream.setFont(font, size);
            stream.newLineAtOffset(x, baseline);
            stream.showText(safe(value));
            stream.endText();
        }

        private void textRight(String value, float right, float baseline, PDFont font,
                float size, Color color) throws IOException {
            String printable = safe(value);
            text(printable, right - font.getStringWidth(printable) * size / 1000, baseline, font, size, color);
        }

        private String fit(String value, PDFont font, float size, float maxWidth) throws IOException {
            String printable = safe(value);
            while (!printable.isEmpty() && font.getStringWidth(printable) * size / 1000 > maxWidth) {
                printable = printable.substring(0, printable.length() - 1);
            }
            return printable.equals(safe(value)) ? printable : printable + "...";
        }

        private List<String> wrap(String value, PDFont font, float size, float width) throws IOException {
            List<String> lines = new ArrayList<>();
            for (String paragraph : safe(value).split("\\R", -1)) {
                String line = "";
                for (String word : paragraph.split("\\s+")) {
                    if (word.isBlank()) continue;
                    String candidate = line.isEmpty() ? word : line + " " + word;
                    if (font.getStringWidth(candidate) * size / 1000 <= width) {
                        line = candidate;
                    } else {
                        if (!line.isEmpty()) lines.add(line);
                        line = fit(word, font, size, width - 12);
                    }
                }
                lines.add(line);
            }
            return lines;
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

