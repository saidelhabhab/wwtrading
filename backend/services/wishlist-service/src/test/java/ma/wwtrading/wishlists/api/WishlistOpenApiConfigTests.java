package ma.wwtrading.wishlists.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.junit.jupiter.api.Test;

class WishlistOpenApiConfigTests {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void configuresBearerJwtSecurityScheme() {
        OpenAPI openApi = config.wishlistServiceOpenApi();

        var scheme = openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_SCHEME);
        assertThat(scheme.getType().toString()).isEqualTo("http");
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
        assertThat(openApi.getSecurity().getFirst().containsKey(OpenApiConfig.BEARER_SCHEME)).isTrue();
    }

    @Test
    void documentsExistingProtectedWishlistOperations() {
        OpenAPI openApi = config.wishlistServiceOpenApi();
        openApi.setPaths(new Paths()
            .addPathItem("/api/wishlist", new PathItem().get(new Operation()))
            .addPathItem("/api/wishlist/items", new PathItem()
                .post(new Operation())
                .delete(new Operation()))
            .addPathItem("/api/wishlist/items/{itemId}", new PathItem().delete(new Operation())));

        config.wishlistEndpointResponses().customise(openApi);

        assertResponses(openApi, "/api/wishlist", PathItem.HttpMethod.GET,
            "200", "401", "403", "500");
        assertResponses(openApi, "/api/wishlist/items", PathItem.HttpMethod.POST,
            "201", "400", "401", "403", "409", "500");
        assertResponses(openApi, "/api/wishlist/items", PathItem.HttpMethod.DELETE,
            "204", "401", "403", "500");
        assertResponses(openApi, "/api/wishlist/items/{itemId}", PathItem.HttpMethod.DELETE,
            "204", "400", "401", "403", "404", "500");
    }

    @Test
    void runtimeConfigurationUsesEnvironmentBackedDatabaseAndJwtProperties() throws IOException {
        Path configuration = Path.of(
            "../config-server/src/main/resources/config-repository/wishlist-service.yml"
        );
        String content = Files.readString(configuration);

        assertThat(content)
            .contains("${WISHLIST_DB_URL:")
            .contains("${WISHLIST_DB_USERNAME:")
            .contains("${WISHLIST_DB_PASSWORD}")
            .contains("flyway:")
            .contains("enabled: true")
            .contains("${JWT_ISSUER_URI:")
            .contains("${JWT_JWK_SET_URI:")
            .contains("${JWT_AUDIENCE:");
    }

    private void assertResponses(
        OpenAPI openApi,
        String path,
        PathItem.HttpMethod method,
        String... responseCodes
    ) {
        Operation operation = openApi.getPaths().get(path).readOperationsMap().get(method);
        assertThat(operation.getResponses()).containsKeys(responseCodes);
        assertThat(operation.getSecurity().getFirst().containsKey(OpenApiConfig.BEARER_SCHEME)).isTrue();
    }
}
