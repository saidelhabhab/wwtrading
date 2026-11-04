package ma.wwtrading.notifications.infrastructure.email;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import ma.wwtrading.notifications.domain.NotificationEntity;

@Service
public class NotificationEmailService {

    private static final Logger log = LoggerFactory.getLogger(NotificationEmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;
    private final String appBaseUrl;

    public NotificationEmailService(
            JavaMailSender mailSender,
            @Value("${wwtrading.mail.from:no-reply@wwtrading.ma}") String fromAddress,
            @Value("${wwtrading.mail.from-name:WWTRADING}") String fromName,
            @Value("${TRACKING_BASE_URL:${FRONTEND_PUBLIC_URL:${wwtrading.app-base-url:${APP_BASE_URL:http://localhost:4201}}}}") String appBaseUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.appBaseUrl = appBaseUrl;
    }

    public void sendNotificationEmail(NotificationEntity notification) {
        if (notification == null) {
            return;
        }

        String recipientEmail = notification.getRecipientEmail();
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new NotificationEmailException("Recipient email is missing", null);
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(new InternetAddress(fromAddress, fromName));
            helper.setTo(recipientEmail.trim());
            helper.setSubject(notification.getTitle());

            String htmlBody = buildHtmlTemplate(
                    notification.getTitle(),
                    notification.getMessage(),
                    notification.getActionUrl(),
                    "ORDER".equals(notification.getEntityType()),
                    notification.getRecipientSubject());

            helper.setText(buildPlainText(notification), htmlBody);

            mailSender.send(message);
            log.info("Sent notification email to {} for notification ID {}", recipientEmail, notification.getId());
        } catch (Exception e) {
            throw new NotificationEmailException("SMTP delivery failed", e);
        }
    }

    String buildPlainText(NotificationEntity notification) {
        StringBuilder text = new StringBuilder()
            .append(notification.getTitle()).append("\n\n")
            .append(notification.getMessage());
        if (notification.getActionUrl() != null && !notification.getActionUrl().isBlank()) {
            text.append("\n\n").append(buildFullActionUrl(notification.getActionUrl()));
        }
        return text.append("\n\nWWTRADING · Kénitra, Maroc").toString();
    }

    String buildHtmlTemplate(String title, String messageContent, String actionUrl, boolean orderStyle,
            String recipientSubject) {
        String escapedTitle = HtmlUtils.htmlEscape(title != null ? title : "");
        String escapedMessage = HtmlUtils.htmlEscape(messageContent != null ? messageContent : "");

        String actionButtonHtml = "";
        if (actionUrl != null && !actionUrl.isBlank()) {
            String fullUrl = buildFullActionUrl(actionUrl);
            String buttonLabel;
            String buttonSubtext;
            if (orderStyle) {
                if (actionUrl.contains("/client/orders/")) {
                    buttonLabel = "Accéder à mon espace commande →";
                    buttonSubtext = "Consultez l'avancement, vos factures et gérez votre commande depuis votre espace client.";
                } else if (actionUrl.contains("/suivi-commande")) {
                    buttonLabel = "Suivre ma commande en direct →";
                    buttonSubtext = "Suivez l'avancement en temps réel et téléversez vos reçus de virement.";
                } else {
                    buttonLabel = "Consulter ma commande →";
                    buttonSubtext = "";
                }
            } else {
                buttonLabel = "Consulter";
                buttonSubtext = "";
            }

            String subtextHtml = !buttonSubtext.isBlank()
                    ? "<p style=\"margin: 8px 0 0; font-size: 12px; color: #64748b;\">" + buttonSubtext + "</p>"
                    : "";

            actionButtonHtml = """
                <div style="text-align: center; margin: 32px 0 16px;">
                    <a href="%s" style="display: inline-block; background: linear-gradient(135deg, #af0f50 0%%, #8f0c41 100%%); color: #ffffff; font-weight: 700; font-size: 15px; padding: 14px 34px; text-decoration: none; border-radius: 999px; box-shadow: 0 4px 14px rgba(175, 15, 80, 0.28); letter-spacing: 0.3px;" target="_blank" rel="noopener">%s</a>
                    %s
                    <p style="margin: 12px 0 0; font-size: 11px; color: #94a3b8; word-break: break-all;">Lien direct : <a href="%s" style="color: #64748b; text-decoration: underline;" target="_blank" rel="noopener">%s</a></p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(fullUrl), buttonLabel, subtextHtml, HtmlUtils.htmlEscape(fullUrl), HtmlUtils.htmlEscape(fullUrl));
        }

        String subtitle = orderStyle
                ? "Expertise Konica Minolta &amp; Develop · Votre partenaire impression professionnel"
                : "Expertise Konica Minolta &amp; Develop";

        String contentBodyHtml;
        if (orderStyle && messageContent != null && !messageContent.isBlank()) {
            contentBodyHtml = formatStructuredOrderHtml(title, messageContent, recipientSubject);
        } else {
            String subjectHtml = (recipientSubject != null && !recipientSubject.isBlank() && orderStyle)
                    ? "<p style=\"margin: 0 0 14px; font-size: 15px; color: #334155; font-weight: 600;\">Bonjour "
                        + HtmlUtils.htmlEscape(recipientSubject) + ",</p>"
                    : "";
            contentBodyHtml = "<h2 class=\"title\">" + escapedTitle + "</h2>\n"
                    + subjectHtml + "\n"
                    + "<div class=\"message\">" + escapedMessage.replace("\n", "<br>") + "</div>";
        }

        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f1f5f9; margin: 0; padding: 24px 12px; color: #0f172a; }
                    .container { max-width: 620px; margin: 0 auto; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 14px; overflow: hidden; box-shadow: 0 10px 25px -5px rgba(15, 23, 42, 0.08), 0 8px 10px -6px rgba(15, 23, 42, 0.04); }
                    .header { background: linear-gradient(135deg, #0f172a 0%%, #1e293b 100%%); color: #ffffff; padding: 30px 24px; text-align: center; }
                    .header h1 { margin: 0; font-size: 24px; font-weight: 900; letter-spacing: 2.5px; color: #ffffff; }
                    .header p { margin: 8px 0 0; font-size: 12px; color: #94a3b8; letter-spacing: 0.5px; }
                    .accent-bar { height: 4px; background: linear-gradient(90deg, #af0f50 0%%, #d02870 50%%, #af0f50 100%%); }
                    .content { padding: 32px 28px 24px; }
                    .title { font-size: 20px; font-weight: 800; color: #0f172a; margin-top: 0; margin-bottom: 12px; line-height: 1.3; }
                    .message { font-size: 14px; line-height: 1.65; color: #334155; margin-bottom: 24px; }
                    .footer { background: #f8fafc; padding: 24px; text-align: center; font-size: 12px; color: #64748b; border-top: 1px solid #e2e8f0; line-height: 1.7; }
                    .footer p { margin: 3px 0; }
                    @media only screen and (max-width: 480px) {
                        body { padding: 8px 4px; }
                        .content { padding: 24px 18px 18px; }
                        .header { padding: 24px 16px; }
                        .header h1 { font-size: 20px; }
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>WWTRADING</h1>
                        <p>%s</p>
                    </div>
                    <div class="accent-bar"></div>
                    <div class="content">
                        %s
                        %s
                    </div>
                    <div class="footer">
                        <p><strong>WWTRADING SARL</strong> · Solutions d'impression &amp; Multifonctions professionnels</p>
                        <p>Service client : <strong>contact@wwtrading.ma</strong> · Kénitra, Maroc</p>
                        <p style="color: #94a3b8; font-size: 11px; margin-top: 8px;">Cet e-mail automatique est lié à l'activité de votre compte ou commande WWTRADING.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(subtitle, contentBodyHtml, actionButtonHtml);
    }

    String buildFullActionUrl(String actionUrl) {
        if (actionUrl == null || actionUrl.isBlank()) {
            return appBaseUrl;
        }
        String trimmed = actionUrl.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        String base = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
        String path = trimmed.startsWith("/") ? trimmed : "/" + trimmed;
        return base + path;
    }

    private String formatStructuredOrderHtml(String title, String messageContent, String recipientSubject) {
        StringBuilder sb = new StringBuilder();
        String escapedTitle = HtmlUtils.htmlEscape(title != null ? title : "");
        String orderRef = extractOrderRef(title, messageContent);

        sb.append("<div style=\"margin-bottom: 22px;\">");
        if (orderRef != null) {
            sb.append("<div style=\"display: inline-block; background-color: #f1f5f9; color: #475569; font-size: 12px; font-weight: 700; padding: 4px 10px; border-radius: 6px; margin-bottom: 10px;\">")
              .append("Commande ").append(HtmlUtils.htmlEscape(orderRef)).append("</div>");
        }
        sb.append("<h2 class=\"title\" style=\"margin-top: 4px; margin-bottom: 6px;\">").append(escapedTitle).append("</h2>");
        sb.append("</div>");

        String statusLabel = extractStatusUpdate(messageContent, title);
        if (statusLabel != null) {
            sb.append(renderStatusCard(statusLabel));
        }

        String[] lines = messageContent.split("\\r?\\n");
        int i = 0;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                i++;
                continue;
            }

            if (line.matches("(?i)^Bonjour.*")) {
                sb.append("<p style=\"font-size: 15px; font-weight: 600; color: #0f172a; margin: 0 0 14px;\">")
                  .append(HtmlUtils.htmlEscape(line)).append("</p>");
                i++;
            } else if (line.matches("(?i)^Votre commande a bien [eé?]t[eé?] enregistr[eé?].*")) {
                sb.append("<p style=\"font-size: 14px; color: #334155; line-height: 1.6; margin: 0 0 16px;\">")
                  .append(HtmlUtils.htmlEscape(line)).append("</p>");
                i++;
            } else if (line.matches("(?i)^R[eé?]f[eé?]rence de commande\\s*:.*") || line.matches("(?i)^Date de commande\\s*:.*")) {
                sb.append("<div style=\"background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 10px 14px; margin-bottom: 16px; font-size: 13px; color: #334155;\">");
                while (i < lines.length && (lines[i].trim().matches("(?i)^R[eé?]f[eé?]rence de commande\\s*:.*") || lines[i].trim().matches("(?i)^Date de commande\\s*:.*"))) {
                    String[] parts = lines[i].trim().split(":", 2);
                    sb.append("<div style=\"margin: 2px 0;\"><strong style=\"color: #0f172a;\">").append(HtmlUtils.htmlEscape(parts[0].trim()))
                      .append(" : </strong>").append(parts.length > 1 ? HtmlUtils.htmlEscape(parts[1].trim()) : "").append("</div>");
                    i++;
                }
                sb.append("</div>");
            } else if (line.matches("(?i)^Articles\\s+command[eé?]s\\s*:.*")) {
                i++;
                List<String> itemLines = new ArrayList<>();
                while (i < lines.length && lines[i].trim().startsWith("- ")) {
                    itemLines.add(lines[i].trim().substring(2));
                    i++;
                }
                sb.append(renderItemsTable(itemLines));
            } else if (line.matches("(?i)^R[eé?]capitulatif\\s+financier\\s*:.*")) {
                i++;
                List<String> financialLines = new ArrayList<>();
                while (i < lines.length && lines[i].trim().startsWith("- ")) {
                    financialLines.add(lines[i].trim().substring(2));
                    i++;
                }
                sb.append(renderFinancialCard(financialLines));
            } else if (line.matches("(?i)^Mode\\s+de\\s+r[eé?]ception\\s*:.*")) {
                i++;
                List<String> receptionLines = new ArrayList<>();
                while (i < lines.length && lines[i].trim().startsWith("- ")) {
                    receptionLines.add(lines[i].trim().substring(2));
                    i++;
                }
                sb.append(renderReceptionCard(receptionLines));
            } else if (line.matches("(?i)^Mode\\s+de\\s+paiement.*")) {
                i++;
                List<String> paymentLines = new ArrayList<>();
                while (i < lines.length && lines[i].trim().startsWith("- ")) {
                    paymentLines.add(lines[i].trim().substring(2));
                    i++;
                }
                sb.append(renderPaymentCard(paymentLines, orderRef));
            } else if (line.matches("(?i)^Lien\\s+de\\s+suivi.*")) {
                i++;
            } else if (line.matches("(?i)^Service\\s+client.*")) {
                i++;
            } else {
                if (statusLabel == null || !line.matches("(?i).*a [eé?]t[eé?] mis [aà?] jour\\s*:.*")) {
                    sb.append("<p style=\"font-size: 14px; color: #475569; line-height: 1.6; margin: 0 0 12px;\">")
                      .append(HtmlUtils.htmlEscape(line)).append("</p>");
                }
                i++;
            }
        }
        return sb.toString();
    }

    private String extractOrderRef(String title, String message) {
        Pattern pattern = Pattern.compile("WWT-[A-Z0-9]{8}", Pattern.CASE_INSENSITIVE);
        if (title != null) {
            Matcher m = pattern.matcher(title);
            if (m.find()) return m.group();
        }
        if (message != null) {
            Matcher m = pattern.matcher(message);
            if (m.find()) return m.group();
        }
        return null;
    }

    private String extractStatusUpdate(String message, String title) {
        if (message != null && message.contains("a été mis à jour :")) {
            String[] parts = message.split("a été mis à jour :", 2);
            if (parts.length > 1) {
                String statusPart = parts[1].trim().split("\\n")[0].trim().replaceAll("\\.$", "");
                if (!statusPart.isBlank()) return statusPart;
            }
        }
        if (title != null && title.contains("—")) {
            String[] parts = title.split("—", 2);
            if (parts.length > 1) {
                String statusPart = parts[1].trim();
                if (!statusPart.isBlank() && !statusPart.startsWith("Confirmation")) {
                    return statusPart;
                }
            }
        }
        return null;
    }

    private String renderStatusCard(String status) {
        String cleanStatus = status.trim().replaceAll("\\.$", "");
        String bg = "#eff6ff";
        String color = "#1d4ed8";
        String border = "#bfdbfe";
        String icon = "⚙";

        String lower = cleanStatus.toLowerCase(Locale.ROOT);
        if (lower.contains("terminée") || lower.contains("livrée") || lower.contains("confirmé") || lower.contains("payé")) {
            bg = "#ecfdf5";
            color = "#047857";
            border = "#a7f3d0";
            icon = "✓";
        } else if (lower.contains("attente") || lower.contains("acompte")) {
            bg = "#fffbeb";
            color = "#b45309";
            border = "#fde68a";
            icon = "⏳";
        } else if (lower.contains("annul")) {
            bg = "#fef2f2";
            color = "#b91c1c";
            border = "#fecaca";
            icon = "✕";
        }

        return """
            <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; margin: 18px 0 22px; text-align: center;">
                <p style="margin: 0 0 10px; font-size: 11px; text-transform: uppercase; letter-spacing: 1px; color: #64748b; font-weight: 700;">Statut de votre commande</p>
                <div style="display: inline-block; background-color: %s; color: %s; border: 1px solid %s; padding: 7px 22px; border-radius: 999px; font-size: 15px; font-weight: 800; letter-spacing: 0.3px;">
                    <span style="margin-right: 6px;">%s</span> %s
                </div>
            </div>
            """.formatted(bg, color, border, icon, HtmlUtils.htmlEscape(cleanStatus));
    }

    private String renderItemsTable(List<String> itemLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"margin: 22px 0;\">")
          .append("<div style=\"font-size: 13px; font-weight: 800; text-transform: uppercase; letter-spacing: 0.8px; color: #0f172a; margin-bottom: 10px;\">Articles commandés</div>")
          .append("<table style=\"width: 100%; border-collapse: separate; border-spacing: 0; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden; font-size: 13px;\">")
          .append("<thead><tr style=\"background-color: #f8fafc; color: #475569; font-size: 11px; text-transform: uppercase; letter-spacing: 0.5px;\">")
          .append("<th style=\"padding: 10px 12px; border-bottom: 1px solid #e2e8f0; text-align: left;\">Article</th>")
          .append("<th style=\"padding: 10px 12px; border-bottom: 1px solid #e2e8f0; text-align: center;\">Qté</th>")
          .append("<th style=\"padding: 10px 12px; border-bottom: 1px solid #e2e8f0; text-align: right;\">Prix unit.</th>")
          .append("<th style=\"padding: 10px 12px; border-bottom: 1px solid #e2e8f0; text-align: right;\">Total</th>")
          .append("</tr></thead><tbody>");

        boolean alt = false;
        for (String itemLine : itemLines) {
            String bg = alt ? "#fafafa" : "#ffffff";
            alt = !alt;

            String name = itemLine;
            String qty = "1";
            String unitPrice = "";
            String total = "";

            if (itemLine.contains(" = ")) {
                String[] partsEq = itemLine.split(" = ", 2);
                total = partsEq[1].trim();
                String left = partsEq[0].trim();
                if (left.contains(" — ")) {
                    String[] partsDash = left.split(" — ", 2);
                    unitPrice = partsDash[1].replace("/ unité", "").trim();
                    String productAndQty = partsDash[0].trim();
                    if (productAndQty.contains(" × ")) {
                        String[] pq = productAndQty.split(" × ", 2);
                        name = pq[0].trim();
                        qty = pq[1].trim();
                    } else {
                        name = productAndQty;
                    }
                }
            }

            sb.append("<tr style=\"background-color: ").append(bg).append(";\">")
              .append("<td style=\"padding: 10px 12px; border-bottom: 1px solid #f1f5f9; font-weight: 600; color: #0f172a;\">").append(HtmlUtils.htmlEscape(name)).append("</td>")
              .append("<td style=\"padding: 10px 12px; border-bottom: 1px solid #f1f5f9; text-align: center; color: #475569;\">").append(HtmlUtils.htmlEscape(qty)).append("</td>")
              .append("<td style=\"padding: 10px 12px; border-bottom: 1px solid #f1f5f9; text-align: right; color: #475569;\">").append(HtmlUtils.htmlEscape(unitPrice)).append("</td>")
              .append("<td style=\"padding: 10px 12px; border-bottom: 1px solid #f1f5f9; text-align: right; font-weight: 700; color: #0f172a;\">").append(HtmlUtils.htmlEscape(total.isEmpty() ? itemLine : total)).append("</td>")
              .append("</tr>");
        }

        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private String renderFinancialCard(List<String> financialLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"margin: 20px 0; background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; padding: 16px 20px;\">")
          .append("<div style=\"font-size: 13px; font-weight: 800; text-transform: uppercase; letter-spacing: 0.8px; color: #0f172a; margin-bottom: 10px;\">Récapitulatif financier</div>")
          .append("<table style=\"width: 100%; border-collapse: collapse; font-size: 13px;\">");

        for (String line : financialLines) {
            String[] parts = line.split(":", 2);
            String label = parts[0].trim();
            String value = parts.length > 1 ? parts[1].trim() : "";
            boolean isTotal = label.toLowerCase(Locale.ROOT).contains("total ttc");

            if (isTotal) {
                sb.append("<tr style=\"border-top: 2px solid #cbd5e1;\">")
                  .append("<td style=\"padding: 10px 0 2px; font-size: 15px; font-weight: 800; color: #0f172a;\">").append(HtmlUtils.htmlEscape(label)).append("</td>")
                  .append("<td style=\"padding: 10px 0 2px; font-size: 17px; font-weight: 800; color: #af0f50; text-align: right;\">").append(HtmlUtils.htmlEscape(value)).append("</td>")
                  .append("</tr>");
            } else {
                sb.append("<tr>")
                  .append("<td style=\"padding: 5px 0; color: #475569;\">").append(HtmlUtils.htmlEscape(label)).append("</td>")
                  .append("<td style=\"padding: 5px 0; color: #0f172a; font-weight: 600; text-align: right;\">").append(HtmlUtils.htmlEscape(value)).append("</td>")
                  .append("</tr>");
            }
        }

        sb.append("</table></div>");
        return sb.toString();
    }

    private String renderReceptionCard(List<String> receptionLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"margin: 18px 0; background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 14px 18px; font-size: 13px;\">")
          .append("<strong style=\"color: #0f172a;\">📦 Mode de réception :</strong> ");
        for (int j = 0; j < receptionLines.size(); j++) {
            if (j > 0) sb.append(" · ");
            sb.append("<span style=\"color: #334155;\">").append(HtmlUtils.htmlEscape(receptionLines.get(j))).append("</span>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private String renderPaymentCard(List<String> paymentLines, String orderRef) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"margin: 22px 0; background: linear-gradient(180deg, #fdf6f9 0%, #ffffff 100%); border: 1px solid #f2d5e2; border-left: 4px solid #af0f50; border-radius: 10px; padding: 18px 20px;\">")
          .append("<div style=\"font-size: 13px; font-weight: 800; color: #880b3d; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 12px;\">🏦 Modalités de paiement &amp; Virement</div>");

        String bank = null;
        String beneficiary = null;
        String rib = null;
        String motif = orderRef;

        sb.append("<div style=\"font-size: 13px; color: #334155; margin-bottom: 10px;\">");
        for (String pLine : paymentLines) {
            String[] parts = pLine.split(":", 2);
            String label = parts[0].trim();
            String val = parts.length > 1 ? parts[1].trim() : "";

            if (label.equalsIgnoreCase("Banque")) {
                bank = val;
            } else if (label.equalsIgnoreCase("Bénéficiaire")) {
                beneficiary = val;
            } else if (label.equalsIgnoreCase("RIB")) {
                rib = val;
            } else if (label.equalsIgnoreCase("Motif du virement")) {
                motif = val;
            } else {
                sb.append("<div style=\"margin: 4px 0;\"><strong style=\"color: #0f172a;\">").append(HtmlUtils.htmlEscape(label)).append(" : </strong>")
                  .append(HtmlUtils.htmlEscape(val)).append("</div>");
            }
        }
        sb.append("</div>");

        if (rib != null || bank != null) {
            sb.append("<div style=\"background: #ffffff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px 16px; margin-top: 10px;\">")
              .append("<table style=\"width: 100%; font-size: 13px; line-height: 1.8;\">");
            if (bank != null) {
                sb.append("<tr><td style=\"color: #64748b; width: 105px;\">Banque :</td><td style=\"font-weight: 700; color: #0f172a;\">").append(HtmlUtils.htmlEscape(bank)).append("</td></tr>");
            }
            if (beneficiary != null) {
                sb.append("<tr><td style=\"color: #64748b;\">Bénéficiaire :</td><td style=\"font-weight: 700; color: #0f172a;\">").append(HtmlUtils.htmlEscape(beneficiary)).append("</td></tr>");
            }
            if (rib != null) {
                sb.append("<tr><td style=\"color: #64748b; vertical-align: middle;\">RIB :</td><td><code style=\"background: #f1f5f9; padding: 4px 8px; border-radius: 4px; font-family: monospace; font-size: 13px; font-weight: 700; color: #0f172a; letter-spacing: 0.5px;\">")
                  .append(HtmlUtils.htmlEscape(rib)).append("</code></td></tr>");
            }
            if (motif != null) {
                sb.append("<tr><td style=\"color: #64748b;\">Motif :</td><td style=\"font-weight: 700; color: #af0f50;\">").append(HtmlUtils.htmlEscape(motif)).append("</td></tr>");
            }
            sb.append("</table></div>");
        }

        sb.append("</div>");
        return sb.toString();
    }
}
