package ma.ceac.telereleve.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterRepository;
import ma.ceac.telereleve.meter.MeterStatus;
import ma.ceac.telereleve.meter.MeterType;
import ma.ceac.telereleve.reading.ReadingIngestionService.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ReadingIngestionServiceTest {

    private static final String TOPIC = "ceac/meters/CEAC-E-000123/readings";
    private static final String MESSAGE = """
            {"serial":"CEAC-E-000123","type":"ELECTRICITY","timestamp":"2026-10-12T10:15:00Z",
             "index":15234.75,"unit":"kWh","voltage":229.8,"signalStrength":-71}""";

    @Mock
    private MeterRepository meters;
    @Mock
    private ReadingRepository readings;
    @Mock
    private ApplicationEventPublisher events;

    private ReadingIngestionService service;
    private final Meter meter = new Meter("CEAC-E-000123", MeterType.ELECTRICITY, "Foyer", "Fès", 34, -5, 15, MeterStatus.ACTIVE);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-12T10:16:00Z"), ZoneOffset.UTC);
        service = new ReadingIngestionService(meters, readings, events,
                new ObjectMapper().registerModule(new JavaTimeModule()), clock, new IngestionMonitor(clock));
    }

    @Test
    void storesANewReadingThenUpdatesTheMeterAndAnnouncesIt() {
        when(meters.findBySerial("CEAC-E-000123")).thenReturn(Optional.of(meter));
        when(readings.insertIfAbsent(any())).thenReturn(true);

        assertThat(service.ingest(TOPIC, MESSAGE)).isEqualTo(Outcome.STORED);

        ArgumentCaptor<StoredReading> stored = ArgumentCaptor.forClass(StoredReading.class);
        verify(readings).insertIfAbsent(stored.capture());
        assertThat(stored.getValue().readAt()).isEqualTo(Instant.parse("2026-10-12T10:15:00Z"));
        assertThat(stored.getValue().index()).isEqualByComparingTo("15234.75");
        assertThat(stored.getValue().voltage()).isEqualByComparingTo("229.8");
        assertThat(stored.getValue().signalStrength()).isEqualTo(-71);
        verify(meters).advanceLastReadingAt(meter.getId(), Instant.parse("2026-10-12T10:15:00Z"));
        verify(events).publishEvent(any(ReadingSavedEvent.class));
    }

    @Test
    void ignoresADuplicateSilently() {
        when(meters.findBySerial("CEAC-E-000123")).thenReturn(Optional.of(meter));
        when(readings.insertIfAbsent(any())).thenReturn(false);

        assertThat(service.ingest(TOPIC, MESSAGE)).isEqualTo(Outcome.DUPLICATE);

        verify(meters, never()).advanceLastReadingAt(any(), any());
        verifyNoInteractions(events);
    }

    @Test
    void dropsInvalidJsonWithoutTouchingTheDatabase() {
        assertThat(service.ingest(TOPIC, "{not json")).isEqualTo(Outcome.REJECTED);
        assertThat(service.ingest(TOPIC, "null")).isEqualTo(Outcome.REJECTED);

        verifyNoInteractions(meters, readings, events);
    }

    @Test
    void dropsAReadingOfAnUnknownMeter() {
        when(meters.findBySerial(anyString())).thenReturn(Optional.empty());

        assertThat(service.ingest(TOPIC, MESSAGE)).isEqualTo(Outcome.REJECTED);

        verifyNoInteractions(readings, events);
    }

    @Test
    void extractsTheSerialFromTheTopic() {
        assertThat(ReadingIngestionService.serialFromTopic(TOPIC)).isEqualTo("CEAC-E-000123");
        assertThat(ReadingIngestionService.serialFromTopic("other/topic")).isEmpty();
        assertThat(ReadingIngestionService.serialFromTopic(null)).isEmpty();
    }
}
