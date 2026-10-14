package ma.ceac.telereleve.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI: http://localhost:8080/swagger-ui.html */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer";

    @Bean
    OpenAPI telereleveOpenApi() {
        // "Authorize" button in Swagger UI: paste the token returned by POST /api/auth/login.
        return new OpenAPI()
                .info(new Info()
                        .title("CEAC Télérelève API")
                        .version("0.1.0")
                        .description("Remote reading of electricity and water meters: meter park, readings, "
                                + "consumption, anomaly alerts. All data is simulated. Sign in with "
                                + "POST /api/auth/login, then use Authorize with the returned token."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
