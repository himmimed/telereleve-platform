package ma.ceac.telereleve.meter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.ceac.telereleve.meter.MeterStatus;
import ma.ceac.telereleve.meter.MeterType;

/** Body of POST and PUT /api/meters. Validation messages are shown to users, hence in French. */
@SerialMatchesType
public record MeterRequest(
        @Schema(example = "CEAC-E-000123")
        @NotBlank(message = "Le numéro de série est obligatoire.")
        @Pattern(regexp = MeterRequest.SERIAL_REGEX, message = "Format attendu : CEAC-E-000123 ou CEAC-W-000123.")
        String serial,

        @NotNull(message = "Le type est obligatoire.")
        MeterType type,

        @NotBlank(message = "Le nom du client est obligatoire.")
        @Size(max = 120, message = "120 caractères au maximum.")
        String clientName,

        @NotBlank(message = "L’adresse est obligatoire.")
        @Size(max = 255, message = "255 caractères au maximum.")
        String address,

        @NotNull(message = "La latitude est obligatoire.")
        @DecimalMin(value = "-90", message = "La latitude doit être comprise entre -90 et 90.")
        @DecimalMax(value = "90", message = "La latitude doit être comprise entre -90 et 90.")
        Double latitude,

        @NotNull(message = "La longitude est obligatoire.")
        @DecimalMin(value = "-180", message = "La longitude doit être comprise entre -180 et 180.")
        @DecimalMax(value = "180", message = "La longitude doit être comprise entre -180 et 180.")
        Double longitude,

        @Schema(example = "15")
        @NotNull(message = "L’intervalle de relève est obligatoire.")
        @Min(value = 1, message = "L’intervalle doit être compris entre 1 et 1440 minutes.")
        @Max(value = 1440, message = "L’intervalle doit être compris entre 1 et 1440 minutes.")
        Integer readingIntervalMin,

        @NotNull(message = "Le statut est obligatoire.")
        MeterStatus status) {

    public static final String SERIAL_REGEX = "^CEAC-[EW]-\\d{6}$";
}
