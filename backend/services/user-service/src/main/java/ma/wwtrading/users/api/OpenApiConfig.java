package ma.wwtrading.users.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI userServiceOpenApi() {
        String bearerScheme = "keycloakBearer";
        return new OpenAPI()
            .info(new Info()
                .title("WWTRADING User API")
                .version("v1")
                .description("Authenticated profile synchronization and management"))
            .addSecurityItem(new SecurityRequirement().addList(bearerScheme))
            .components(new Components().addSecuritySchemes(bearerScheme, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")));
    }
}
