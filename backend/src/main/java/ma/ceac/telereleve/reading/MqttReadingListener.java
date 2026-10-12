package ma.ceac.telereleve.reading;

import ma.ceac.telereleve.config.MqttConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.StaticMessageHeaderAccessor;
import org.springframework.integration.acks.SimpleAcknowledgment;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/** Bridge between the MQTT channel and the ingestion service, with manual acknowledgement. */
@Component
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttReadingListener {

    private static final Logger log = LoggerFactory.getLogger(MqttReadingListener.class);

    private final ReadingIngestionService ingestion;

    public MqttReadingListener(ReadingIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @ServiceActivator(inputChannel = MqttConfig.READINGS_CHANNEL)
    public void onMessage(Message<String> message) {
        String topic = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class);
        try {
            ingestion.ingest(topic, message.getPayload());
        } catch (RuntimeException e) {
            // Infrastructure failure (database down, ...): do NOT acknowledge, so the broker redelivers
            // the reading after reconnection instead of it being lost.
            log.error("Reading not processed, left unacknowledged for redelivery (topic={})", topic, e);
            return;
        }
        SimpleAcknowledgment ack = StaticMessageHeaderAccessor.getAcknowledgment(message);
        if (ack != null) {
            ack.acknowledge();
        }
    }
}
