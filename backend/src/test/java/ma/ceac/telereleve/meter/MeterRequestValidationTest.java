package ma.ceac.telereleve.meter;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Map;
import java.util.stream.Collectors;
import ma.ceac.telereleve.meter.dto.MeterRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MeterRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static MeterRequest valid() {
        return new MeterRequest("CEAC-E-000123", MeterType.ELECTRICITY, "Foyer Alaoui", "1, Rue Ibn Sina, Fès",
                34.03, -5.0, 15, MeterStatus.ACTIVE);
    }

    /** Field name -> message of the violations. */
    private static Map<String, String> errors(MeterRequest request) {
        return validator.validate(request).stream().collect(Collectors.toMap(
                v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage, (a, b) -> a));
    }

    @Test
    void acceptsAValidRequest() {
        assertThat(errors(valid())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CEAC-X-000123", "CEAC-E-12345", "ceac-e-000123", "CEAC-E-0001234", "E-000123"})
    void rejectsMalformedSerials(String serial) {
        MeterRequest request = new MeterRequest(serial, MeterType.ELECTRICITY, "A", "B", 34.0, -5.0, 15, MeterStatus.ACTIVE);
        assertThat(errors(request)).containsKey("serial");
    }

    @Test
    void rejectsAWaterSerialOnAnElectricityMeter() {
        MeterRequest request = new MeterRequest("CEAC-W-000123", MeterType.ELECTRICITY, "A", "B", 34.0, -5.0, 15, MeterStatus.ACTIVE);
        assertThat(errors(request)).containsEntry("serial",
                "Le préfixe du numéro de série ne correspond pas au type (E pour électricité, W pour eau).");
    }

    @Test
    void acceptsAWaterSerialOnAWaterMeter() {
        MeterRequest request = new MeterRequest("CEAC-W-000123", MeterType.WATER, "A", "B", 34.0, -5.0, 15, MeterStatus.ACTIVE);
        assertThat(errors(request)).isEmpty();
    }

    @Test
    void rejectsOutOfRangeCoordinates() {
        MeterRequest request = new MeterRequest("CEAC-E-000123", MeterType.ELECTRICITY, "A", "B", 91.0, -181.0, 15, MeterStatus.ACTIVE);
        assertThat(errors(request)).containsKeys("latitude", "longitude");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1441})
    void rejectsAReadingIntervalOutside1To1440Minutes(int interval) {
        MeterRequest request = new MeterRequest("CEAC-E-000123", MeterType.ELECTRICITY, "A", "B", 34.0, -5.0, interval, MeterStatus.ACTIVE);
        assertThat(errors(request)).containsKey("readingIntervalMin");
    }

    @Test
    void requiresTheMandatoryFields() {
        MeterRequest request = new MeterRequest(null, null, " ", "", null, null, null, null);
        assertThat(errors(request)).containsKeys("serial", "type", "clientName", "address",
                "latitude", "longitude", "readingIntervalMin", "status");
    }
}
