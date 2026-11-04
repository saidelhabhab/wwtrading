package ma.wwtrading.products.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ma.wwtrading.products.application.ProductApplicationService;
import ma.wwtrading.products.application.ProductImageApplicationService;
import ma.wwtrading.products.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;

@WebMvcTest
@ContextConfiguration(classes = {ProductController.class, ProductImageController.class, SecurityConfig.class, OpenApiConfig.class})
@TestPropertySource(properties = {
    "wwtrading.security.issuer-uri=https://issuer.example.test/realms/wwtrading",
    "wwtrading.security.jwk-set-uri=https://issuer.example.test/realms/wwtrading/certs",
    "wwtrading.security.audience=wwtrading-api"
})
class ProductSecurityOpenApiTests {

    private static final String ISSUER = "https://issuer.example.test/realms/wwtrading";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductApplicationService service;

    @MockitoBean
    private ProductImageApplicationService imageService;

    @Test
    void protectsWritesAndAllowsClientRead() throws Exception {
        String body = "{\"reference\":\"PR-001\",\"name\":\"Product\",\"price\":10,\"type\":\"PRINTER\"}";
        UUID id = UUID.randomUUID();
        when(service.create(org.mockito.ArgumentMatchers.any())).thenReturn(response(id));
        when(service.update(org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.any())).thenReturn(response(id));
        when(service.activate(id)).thenReturn(response(id));
        when(service.deactivate(id)).thenReturn(response(id));
        when(service.getById(id)).thenReturn(response(id));

        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/products").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT")))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/products/{id}", id).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT")))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/products/{id}/activate", id))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/products/{id}/activate", id)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/products/{id}/deactivate", id)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/products/{id}", id).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
            .andExpect(status().isOk());

        for (String role : List.of("ADMIN", "MANAGER", "COMMERCIAL")) {
            mvc.perform(post("/api/products").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
            mvc.perform(put("/api/products/{id}", id).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
            mvc.perform(post("/api/products/{id}/activate", id)
                    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
            mvc.perform(post("/api/products/{id}/deactivate", id)
                    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
        }
    }

    @Test
    void validatesIssuerAudienceAndStrictRealmRoles() {
        var security = new SecurityConfig();
        assertThat(security.jwtValidator(ISSUER, "wwtrading-api").validate(token(ISSUER, List.of("wwtrading-api"))).hasErrors()).isFalse();
        assertThat(security.jwtValidator(ISSUER, "wwtrading-api").validate(token("https://wrong.example.test", List.of("wwtrading-api"))).hasErrors()).isTrue();
        assertThat(security.jwtValidator(ISSUER, "wwtrading-api").validate(token(ISSUER, List.of("other-api"))).hasErrors()).isTrue();

        var authentication = security.jwtAuthenticationConverter().convert(Jwt.withTokenValue("token")
            .header("alg", "none").subject("user-1")
            .claim("realm_access", Map.of("roles", List.of("CLIENT", "UNTRUSTED"))).build());
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
            .containsExactly("ROLE_CLIENT");
    }

    @Test
    void productImageReadsArePublicAndMutationsRequireAdminOrManager() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        var imageResponse = new ProductImageResponse(
            imageId, "/api/products/" + productId + "/images/" + imageId + "/content",
            true, 0, Instant.now(), Instant.now()
        );
        when(imageService.list(productId)).thenReturn(List.of(imageResponse));
        when(imageService.upload(org.mockito.ArgumentMatchers.eq(productId), org.mockito.ArgumentMatchers.any()))
            .thenReturn(imageResponse);
        when(imageService.setMain(productId, imageId)).thenReturn(imageResponse);
        when(imageService.reorder(org.mockito.ArgumentMatchers.eq(productId), org.mockito.ArgumentMatchers.any()))
            .thenReturn(List.of(imageResponse));

        MockMultipartFile file = new MockMultipartFile(
            "file", "image.png", "image/png",
            new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}
        );
        String order = "{\"imageIds\":[\"" + imageId + "\"]}";

        mvc.perform(get("/api/products/{productId}/images", productId)).andExpect(status().isOk());
        mvc.perform(multipart("/api/products/{productId}/images", productId).file(file))
            .andExpect(status().isUnauthorized());

        for (String deniedRole : List.of("COMMERCIAL", "TECHNICIAN", "CLIENT")) {
            var denied = jwt().authorities(new SimpleGrantedAuthority("ROLE_" + deniedRole));
            mvc.perform(multipart("/api/products/{productId}/images", productId).file(file).with(denied))
                .andExpect(status().isForbidden());
            mvc.perform(put("/api/products/{productId}/images/{imageId}/main", productId, imageId).with(denied))
                .andExpect(status().isForbidden());
            mvc.perform(delete("/api/products/{productId}/images/{imageId}", productId, imageId).with(denied))
                .andExpect(status().isForbidden());
            mvc.perform(put("/api/products/{productId}/images/order", productId).with(denied)
                    .contentType(MediaType.APPLICATION_JSON).content(order))
                .andExpect(status().isForbidden());
        }

        for (String role : List.of("ADMIN", "MANAGER")) {
            mvc.perform(multipart("/api/products/{productId}/images", productId).file(file)
                    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isCreated());
            mvc.perform(put("/api/products/{productId}/images/{imageId}/main", productId, imageId)
                    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
            mvc.perform(delete("/api/products/{productId}/images/{imageId}", productId, imageId)
                    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isNoContent());
            mvc.perform(put("/api/products/{productId}/images/order", productId)
                    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                    .contentType(MediaType.APPLICATION_JSON).content(order))
                .andExpect(status().isOk());
        }
    }

    @Test
    void documentsBearerSecurityAndExpectedResponses() {
        OpenAPI openApi = new OpenApiConfig().productServiceOpenApi();
        openApi.setPaths(new io.swagger.v3.oas.models.Paths()
            .addPathItem("/api/products", new PathItem().get(new Operation()).post(new Operation()))
            .addPathItem("/api/products/{id}", new PathItem().get(new Operation()).put(new Operation()))
            .addPathItem("/api/products/{id}/activate", new PathItem().post(new Operation()))
            .addPathItem("/api/products/{id}/deactivate", new PathItem().post(new Operation())));
        openApi.getPaths()
            .addPathItem("/api/products/{productId}/images", new PathItem().get(new Operation()).post(new Operation()))
            .addPathItem("/api/products/{productId}/images/{imageId}/content", new PathItem().get(new Operation()))
            .addPathItem("/api/products/{productId}/images/{imageId}/main", new PathItem().put(new Operation()))
            .addPathItem("/api/products/{productId}/images/{imageId}", new PathItem().delete(new Operation()))
            .addPathItem("/api/products/{productId}/images/order", new PathItem().put(new Operation()));

        new OpenApiConfig().productEndpointResponses().customise(openApi);

        var scheme = openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_SCHEME);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
        assertThat(openApi.getPaths().get("/api/products").getPost().getSecurity()).isNotEmpty();
        assertThat(openApi.getPaths().get("/api/products").getPost().getResponses()).containsKeys("201", "400", "401", "403", "409", "500");
        assertThat(openApi.getPaths().get("/api/products/{id}/activate").getPost().getSecurity()).isNotEmpty();
        assertThat(openApi.getPaths().get("/api/products/{id}/activate").getPost().getResponses())
            .containsKeys("200", "401", "403", "404", "500");
        assertThat(openApi.getPaths().get("/api/products/{id}/deactivate").getPost().getResponses())
            .containsKeys("200", "401", "403", "404", "500");
        assertThat(openApi.getPaths().get("/api/products/{productId}/images").getGet().getSecurity()).isNullOrEmpty();
        assertThat(openApi.getPaths().get("/api/products/{productId}/images").getPost().getSecurity()).isNotEmpty();
        assertThat(openApi.getPaths().get("/api/products/{productId}/images").getPost().getResponses())
            .containsKeys("201", "400", "401", "403", "404", "413", "415", "500");
        assertThat(openApi.getPaths().get("/api/products/{productId}/images/{imageId}").getDelete().getResponses())
            .containsKeys("204", "400", "401", "403", "404", "500");
    }

    private Jwt token(String issuer, List<String> audience) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token").header("alg", "none").subject("user-1")
            .issuer(issuer).audience(audience).issuedAt(now.minusSeconds(30)).expiresAt(now.plusSeconds(300)).build();
    }

    private ProductResponse response(UUID id) {
        return new ProductResponse(id, "PR-001", "Product", null, BigDecimal.TEN, true, Instant.now(), Instant.now());
    }
}
