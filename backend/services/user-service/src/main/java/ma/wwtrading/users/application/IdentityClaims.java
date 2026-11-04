package ma.wwtrading.users.application;

import org.springframework.security.oauth2.jwt.Jwt;

record IdentityClaims(
    String subject,
    String email,
    String firstName,
    String lastName,
    String locale
) {
    static IdentityClaims from(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank() || email.length() > 320 || !email.contains("@")) {
            throw new MissingIdentityClaimException("The authenticated identity has no valid email claim");
        }
        return new IdentityClaims(
            jwt.getSubject(),
            email,
            jwt.getClaimAsString("given_name"),
            jwt.getClaimAsString("family_name"),
            jwt.getClaimAsString("locale")
        );
    }
}
