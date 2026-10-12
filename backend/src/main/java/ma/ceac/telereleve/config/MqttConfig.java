package ma.ceac.telereleve.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;

/**
 * Subscribes to ceac/meters/+/readings (QoS 1) and sends every message to the "mqttReadings" channel,
 * consumed by MqttReadingListener.
 *
 * Delivery guarantees, chosen for "no reading lost":
 * - persistent session (cleanSession=false) with a fixed client id: while the backend is down or
 *   restarting, the broker queues QoS 1 readings and delivers them on reconnection;
 * - manual acknowledgement: a message is acknowledged only once processed (stored, duplicate or
 *   deliberately rejected). If the database fails, it stays unacknowledged and is redelivered.
 * Messages are processed one at a time on the client thread, which also keeps each meter's readings
 * in arrival order (needed by rule A4, "index lower than the previous one").
 */
@Configuration
@EnableConfigurationProperties(MqttProperties.class)
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttConfig {

    public static final String READINGS_CHANNEL = "mqttReadings";

    @Bean
    MqttPahoClientFactory mqttClientFactory(MqttProperties properties) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] {properties.url()});
        if (properties.username() != null && !properties.username().isBlank()) {
            options.setUserName(properties.username());
            options.setPassword(properties.password() == null ? new char[0] : properties.password().toCharArray());
        }
        options.setCleanSession(false);
        options.setAutomaticReconnect(true);
        // Room for a burst of queued readings (1000 meters) after a restart.
        options.setMaxInflight(1000);
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean(name = READINGS_CHANNEL)
    MessageChannel mqttReadings() {
        return new DirectChannel();
    }

    @Bean
    MessageProducer mqttInbound(MqttProperties properties, MqttPahoClientFactory factory) {
        var adapter = new MqttPahoMessageDrivenChannelAdapter(properties.clientId(), factory, properties.topic());
        adapter.setQos(1);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setManualAcks(true);
        adapter.setCompletionTimeout(10_000);
        adapter.setOutputChannel(mqttReadings());
        return adapter;
    }
}
