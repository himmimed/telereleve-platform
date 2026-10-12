package ma.ceac.telereleve.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI: http://localhost:8080/swagger-ui.html */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI telereleveOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CEAC Télérelève API")
                .version("0.1.0")
                .description("Remote reading of electricity and water meters: meter park, readings, "
                        + "consumption, anomaly alerts. All data is simulated."));
    }
}
