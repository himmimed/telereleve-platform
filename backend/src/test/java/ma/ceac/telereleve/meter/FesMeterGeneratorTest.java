package ma.ceac.telereleve.meter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import ma.ceac.telereleve.meter.dto.MeterRequest;
import org.junit.jupiter.api.Test;

class FesMeterGeneratorTest {

    @Test
    void generatesTheRequestedNumberOfUniqueSerials() {
        List<MeterRequest> meters = FesMeterGenerator.generate(1000, 0.3);

        assertThat(meters).hasSize(1000);
        assertThat(meters).extracting(MeterRequest::serial).doesNotHaveDuplicates()
                .allMatch(serial -> serial.matches(MeterRequest.SERIAL_REGEX));
    }

    @Test
    void respectsTheWaterShareExactly() {
        long water = FesMeterGenerator.generate(50, 0.3).stream().filter(m -> m.type() == MeterType.WATER).count();

        assertThat(water).isEqualTo(15);
    }

    @Test
    void usesTheSerialLetterOfTheType() {
        assertThat(FesMeterGenerator.generate(50, 0.3))
                .allMatch(m -> m.serial().charAt(5) == m.type().serialLetter());
    }

    @Test
    void placesEveryMeterAroundFes() {
        assertThat(FesMeterGenerator.generate(200, 0.3)).allSatisfy(m -> {
            assertThat(m.latitude()).isBetween(33.97, 34.08);
            assertThat(m.longitude()).isBetween(-5.08, -4.95);
        });
    }

    @Test
    void keepsMeterNIdenticalWhenTheParkGrows() {
        List<MeterRequest> small = FesMeterGenerator.generate(50, 0.3);
        List<MeterRequest> large = FesMeterGenerator.generate(1000, 0.3);

        assertThat(large.subList(0, 50)).isEqualTo(small);
    }
}
