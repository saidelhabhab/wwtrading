<#macro emailLayout>
<!doctype html>
<html lang="${locale!'fr'}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>WWTRADING</title>
</head>
<body style="margin:0;padding:0;background:#f5f4f2;color:#1a1a18;font-family:Arial,Helvetica,sans-serif;">
  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="width:100%;background:#f5f4f2;">
    <tr>
      <td align="center" style="padding:32px 16px;">
        <table role="presentation" width="600" cellspacing="0" cellpadding="0" style="width:100%;max-width:600px;border-collapse:separate;border-spacing:0;background:#ffffff;border:1px solid #dedbd7;">
          <tr>
            <td style="height:4px;background:#af0f50;font-size:0;line-height:0;">&nbsp;</td>
          </tr>
          <tr>
            <td style="padding:28px 34px 18px;border-bottom:1px solid #dedbd7;">
              <div style="color:#af0f50;font-size:24px;font-weight:700;letter-spacing:-0.5px;">WWTRADING</div>
              <div style="margin-top:6px;color:#625f5c;font-size:12px;letter-spacing:1.2px;text-transform:uppercase;">${msg("wwtEmailTagline")}</div>
            </td>
          </tr>
          <tr>
            <td style="padding:32px 34px;color:#1a1a18;font-size:15px;line-height:1.65;">
              <#nested>
            </td>
          </tr>
          <tr>
            <td style="padding:20px 34px;background:#1a1a18;color:#d8d5d2;font-size:12px;line-height:1.5;">
              ${msg("wwtEmailFooter")}
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>
</#macro>
