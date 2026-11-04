package ma.wwtrading.wishlists.api;

import java.util.LinkedHashMap;
import java.util.Map;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "keycloakBearer";

    private static final Map<String, String> RESPONSE_DESCRIPTIONS = responseDescriptions();

    @Bean
    public OpenAPI wishlistServiceOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("WWTRADING Wishlist API")
                .version("v1")
                .description("Authenticated customer-owned wishlist operations"))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
            .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")));
    }

    @Bean
    public OpenApiCustomizer wishlistEndpointResponses() {
        return openApi -> {
            document(openApi, "/api/wishlist", PathItem.HttpMethod.GET,
                "200", "401", "403", "500");
            document(openApi, "/api/wishlist/items", PathItem.HttpMethod.POST,
                "201", "400", "401", "403", "409", "500");
            document(openApi, "/api/wishlist/items", PathItem.HttpMethod.DELETE,
                "204", "401", "403", "500");
            document(openApi, "/api/wishlist/items/{itemId}", PathItem.HttpMethod.DELETE,
                "204", "400", "401", "403", "404", "500");
        };
    }

    private void document(OpenAPI openApi, String path, PathItem.HttpMethod method, String... responseCodes) {
        if (openApi.getPaths() == null || openApi.getPaths().get(path) == null) {
            return;
        }
        Operation operation = openApi.getPaths().get(path).readOperationsMap().get(method);
        if (operation == null) {
            return;
        }
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        for (String responseCode : responseCodes) {
            responses.addApiResponse(responseCode,
                new ApiResponse().description(RESPONSE_DESCRIPTIONS.get(responseCode)));
        }
        operation.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    private static Map<String, String> responseDescriptions() {
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("200", "Wishlist returned");
        descriptions.put("201", "Product added to wishlist");
        descriptions.put("204", "Operation completed");
        descriptions.put("400", "Invalid request");
        descriptions.put("401", "Authentication required");
        descriptions.put("403", "Insufficient role");
        descriptions.put("404", "Wishlist item not found");
        descriptions.put("409", "Product already exists in wishlist");
        descriptions.put("500", "Unexpected server error");
        return Map.copyOf(descriptions);
    }
}
