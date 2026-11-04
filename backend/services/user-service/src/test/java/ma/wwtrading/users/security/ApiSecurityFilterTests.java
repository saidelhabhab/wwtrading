package ma.wwtrading.users.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiSecurityFilterTests {

    private final ApiSecurityFilter filter = new ApiSecurityFilter();

    @Test
    void preservesSafeCorrelationIdAndAddsSecurityHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader(ApiSecurityFilter.CORRELATION_HEADER, "request-123");
        request.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(ApiSecurityFilter.CORRELATION_HEADER)).isEqualTo("request-123");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Strict-Transport-Security"))
            .isEqualTo("max-age=31536000; includeSubDomains");
    }

    @Test
    void replacesUnsafeCorrelationId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader(ApiSecurityFilter.CORRELATION_HEADER, "unsafe value with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(ApiSecurityFilter.CORRELATION_HEADER))
            .matches("[0-9a-f-]{36}");
    }
}
