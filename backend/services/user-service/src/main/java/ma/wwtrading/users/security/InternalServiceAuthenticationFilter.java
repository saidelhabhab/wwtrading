package ma.wwtrading.users.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-WWTRADING-Internal-Key";
    private final byte[] expectedKey;

    public InternalServiceAuthenticationFilter(
            @Value("${wwtrading.internal.notification-key:}") String expectedKey) {
        this.expectedKey = expectedKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/internal/notification-recipients");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        byte[] supplied = request.getHeader(HEADER) == null
                ? new byte[0]
                : request.getHeader(HEADER).getBytes(StandardCharsets.UTF_8);
        if (expectedKey.length == 0 || !MessageDigest.isEqual(expectedKey, supplied)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid internal service credential");
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                "notification-service", null, List.of(new SimpleGrantedAuthority("ROLE_NOTIFICATION_SERVICE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
