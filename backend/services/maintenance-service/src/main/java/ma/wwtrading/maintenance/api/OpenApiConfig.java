package ma.wwtrading.maintenance.api;

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
    public OpenAPI maintenanceServiceOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("WWTRADING Maintenance API")
                .version("v1")
                .description("Maintenance request creation, read, lifecycle and technician assignment operations"))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
            .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")));
    }

    @Bean
    public OpenApiCustomizer maintenanceEndpointResponses() {
        return openApi -> {
            document(openApi, "/api/maintenance", PathItem.HttpMethod.POST,
                "201", "400", "401", "403", "409", "500");
            document(openApi, "/api/maintenance", PathItem.HttpMethod.GET,
                "200", "400", "401", "403", "500");
            document(openApi, "/api/maintenance/{id}", PathItem.HttpMethod.GET,
                "200", "400", "401", "403", "404", "500");
            document(openApi, "/api/maintenance/{id}/history", PathItem.HttpMethod.GET,
                "200", "400", "401", "403", "404", "500");
            document(openApi, "/api/maintenance/{id}/start", PathItem.HttpMethod.POST,
                "200", "400", "401", "403", "404", "409", "500");
            document(openApi, "/api/maintenance/{id}/complete", PathItem.HttpMethod.POST,
                "200", "400", "401", "403", "404", "409", "500");
            document(openApi, "/api/maintenance/{id}/cancel", PathItem.HttpMethod.POST,
                "200", "400", "401", "403", "404", "409", "500");
            document(openApi, "/api/maintenance/{id}/technician/{technicianId}", PathItem.HttpMethod.PUT,
                "200", "400", "401", "403", "404", "500");
            document(openApi, "/api/maintenance/{id}/technician", PathItem.HttpMethod.DELETE,
                "200", "400", "401", "403", "404", "500");
        };
    }

    private void document(OpenAPI openApi, String path, PathItem.HttpMethod method, String... responseCodes) {
        if (openApi.getPaths() == null || openApi.getPaths().get(path) == null) return;
        Operation operation = openApi.getPaths().get(path).readOperationsMap().get(method);
        if (operation == null) return;

        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        for (String responseCode : responseCodes) {
            ApiResponse response = responses.get(responseCode);
            if (response == null) response = new ApiResponse();
            response.setDescription(RESPONSE_DESCRIPTIONS.get(responseCode));
            responses.addApiResponse(responseCode, response);
        }
        operation.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    private static Map<String, String> responseDescriptions() {
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("200", "Maintenance request returned");
        descriptions.put("201", "Maintenance request created");
        descriptions.put("400", "Invalid request or validation failure");
        descriptions.put("401", "Authentication required");
        descriptions.put("403", "Insufficient role");
        descriptions.put("404", "Maintenance request not found");
        descriptions.put("409", "Maintenance data conflict");
        descriptions.put("500", "Unexpected server error");
        return Map.copyOf(descriptions);
    }
}
