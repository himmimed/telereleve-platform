package ma.ceac.telereleve.config;

import ma.ceac.telereleve.reading.ConsumptionStep;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** step=hour|day|month in URLs (Spring's default enum conversion is case-sensitive). */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, ConsumptionStep.class, ConsumptionStep::fromCode);
    }
}
