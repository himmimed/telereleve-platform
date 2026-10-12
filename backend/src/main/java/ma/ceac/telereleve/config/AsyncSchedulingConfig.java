package ma.ceac.telereleve.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @EnableScheduling: periodic check of silent meters (rule A3).
 * @EnableAsync: alert e-mails are sent off the ingestion thread (Spring Boot's task executor).
 */
@Configuration
@EnableScheduling
@EnableAsync
public class AsyncSchedulingConfig {
}
