package ma.wwtrading.products.api;

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
    public OpenAPI productServiceOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("WWTRADING Product API")
                .version("v1")
                .description("Product catalogue operations"))
            .components(new Components()
                .addSecuritySchemes(
                    BEARER_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                )
            );
    }

    @Bean
    public OpenApiCustomizer productEndpointResponses() {
        return openApi -> {
            // PUBLIC
            document(
                openApi,
                "/api/products",
                PathItem.HttpMethod.GET,
                "200",
                "500"
            );

            document(
                openApi,
                "/api/products/{id}",
                PathItem.HttpMethod.GET,
                "200",
                "404",
                "500"
            );

            // PROTECTED
            document(
                openApi,
                "/api/products",
                PathItem.HttpMethod.POST,
                "201",
                "400",
                "401",
                "403",
                "409",
                "500"
            );

            document(
                openApi,
                "/api/products/{id}",
                PathItem.HttpMethod.PUT,
                "200",
                "400",
                "401",
                "403",
                "404",
                "409",
                "500"
            );

            document(
                openApi,
                "/api/products/{id}/activate",
                PathItem.HttpMethod.POST,
                "200",
                "401",
                "403",
                "404",
                "500"
            );

            document(
                openApi,
                "/api/products/{id}/deactivate",
                PathItem.HttpMethod.POST,
                "200",
                "401",
                "403",
                "404",
                "500"
            );

            document(openApi, "/api/products/{productId}/images", PathItem.HttpMethod.GET, "200", "404", "500");
            document(openApi, "/api/products/{productId}/images", PathItem.HttpMethod.POST,
                "201", "400", "401", "403", "404", "413", "415", "500");
            document(openApi, "/api/products/{productId}/images/{imageId}/content", PathItem.HttpMethod.GET,
                "200", "404", "500");
            document(openApi, "/api/products/{productId}/images/{imageId}/main", PathItem.HttpMethod.PUT,
                "200", "400", "401", "403", "404", "500");
            document(openApi, "/api/products/{productId}/images/{imageId}", PathItem.HttpMethod.DELETE,
                "204", "400", "401", "403", "404", "500");
            document(openApi, "/api/products/{productId}/images/order", PathItem.HttpMethod.PUT,
                "200", "400", "401", "403", "404", "500");
        };
    }

    private void document(
        OpenAPI openApi,
        String path,
        PathItem.HttpMethod method,
        String... responseCodes
    ) {
        if (openApi.getPaths() == null) {
            return;
        }

        PathItem pathItem = openApi.getPaths().get(path);

        if (pathItem == null) {
            return;
        }

        Operation operation = pathItem
            .readOperationsMap()
            .get(method);

        if (operation == null) {
            return;
        }

        ApiResponses responses = operation.getResponses();

        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }

        for (String responseCode : responseCodes) {
            String description = RESPONSE_DESCRIPTIONS.get(responseCode);

            if (description != null) {
                responses.addApiResponse(
                    responseCode,
                    new ApiResponse().description(description)
                );
            }
        }

        /*
         * Only non-GET operations require authentication.
         *
         * GET /api/products
         * GET /api/products/{id}
         *
         * are public.
         */
        if (method != PathItem.HttpMethod.GET) {
            operation.addSecurityItem(
                new SecurityRequirement()
                    .addList(BEARER_SCHEME)
            );
        }
    }

    private static Map<String, String> responseDescriptions() {
        Map<String, String> descriptions = new LinkedHashMap<>();

        descriptions.put("200", "Product returned or updated");
        descriptions.put("201", "Product created");
        descriptions.put("204", "Request completed without response content");
        descriptions.put("400", "Invalid request");
        descriptions.put("401", "Authentication required");
        descriptions.put("403", "Insufficient role");
        descriptions.put("404", "Product not found");
        descriptions.put("409", "Product reference conflict");
        descriptions.put("413", "Uploaded image exceeds the configured size limit");
        descriptions.put("415", "Unsupported image media type");
        descriptions.put("500", "Unexpected server error");

        return Map.copyOf(descriptions);
    }
}
