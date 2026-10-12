package ma.ceac.telereleve.meter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import ma.ceac.telereleve.common.ConflictException;
import ma.ceac.telereleve.common.NotFoundException;
import ma.ceac.telereleve.meter.dto.MeterRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeterServiceTest {

    @Mock
    private MeterRepository meters;

    @InjectMocks
    private MeterService service;

    private static MeterRequest request(String serial) {
        return new MeterRequest(serial, MeterType.ELECTRICITY, "  Foyer Tazi  ", " 5, Rue Ibn Sina, Fès ",
                34.03, -5.0, 15, MeterStatus.ACTIVE);
    }

    @Test
    void createsAMeterWithTrimmedText() {
        when(meters.existsBySerial("CEAC-E-000001")).thenReturn(false);
        when(meters.save(any(Meter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Meter meter = service.create(request("CEAC-E-000001"));

        assertThat(meter.getSerial()).isEqualTo("CEAC-E-000001");
        assertThat(meter.getClientName()).isEqualTo("Foyer Tazi");
        assertThat(meter.getAddress()).isEqualTo("5, Rue Ibn Sina, Fès");
        assertThat(meter.getReadingIntervalMin()).isEqualTo(15);
    }

    @Test
    void refusesADuplicateSerialOnCreation() {
        when(meters.existsBySerial("CEAC-E-000001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("CEAC-E-000001")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("CEAC-E-000001");
        verify(meters, never()).save(any());
    }

    @Test
    void updatesEveryField() {
        UUID id = UUID.randomUUID();
        Meter existing = new Meter("CEAC-E-000001", MeterType.ELECTRICITY, "Ancien", "Adresse", 34, -5, 15, MeterStatus.ACTIVE);
        when(meters.findById(id)).thenReturn(Optional.of(existing));
        when(meters.existsBySerialAndIdNot("CEAC-E-000002", id)).thenReturn(false);

        Meter updated = service.update(id, new MeterRequest("CEAC-E-000002", MeterType.ELECTRICITY, "Nouveau",
                "Autre adresse", 34.1, -5.1, 30, MeterStatus.INACTIVE));

        assertThat(updated.getSerial()).isEqualTo("CEAC-E-000002");
        assertThat(updated.getReadingIntervalMin()).isEqualTo(30);
        assertThat(updated.getStatus()).isEqualTo(MeterStatus.INACTIVE);
    }

    @Test
    void refusesToTakeTheSerialOfAnotherMeter() {
        UUID id = UUID.randomUUID();
        when(meters.findById(id)).thenReturn(Optional.of(
                new Meter("CEAC-E-000001", MeterType.ELECTRICITY, "A", "B", 34, -5, 15, MeterStatus.ACTIVE)));
        when(meters.existsBySerialAndIdNot("CEAC-E-000002", id)).thenReturn(true);

        assertThatThrownBy(() -> service.update(id, request("CEAC-E-000002"))).isInstanceOf(ConflictException.class);
    }

    @Test
    void reportsAnUnknownMeter() {
        UUID id = UUID.randomUUID();
        when(meters.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(NotFoundException.class);
    }
}
