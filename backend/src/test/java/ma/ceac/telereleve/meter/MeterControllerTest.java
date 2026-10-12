package ma.ceac.telereleve.meter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import ma.ceac.telereleve.common.ConflictException;
import ma.ceac.telereleve.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer only: JSON shape, validation and RFC 7807 errors. The service is mocked. */
@WebMvcTest(MeterController.class)
class MeterControllerTest {

    private static final String VALID_BODY = """
            {"serial":"CEAC-E-000123","type":"ELECTRICITY","clientName":"Foyer Alaoui",
             "address":"1, Rue Ibn Sina, Fès","latitude":34.03,"longitude":-5.0,
             "readingIntervalMin":15,"status":"ACTIVE"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MeterService meterService;

    private static Meter meter() {
        return new Meter("CEAC-E-000123", MeterType.ELECTRICITY, "Foyer Alaoui", "1, Rue Ibn Sina, Fès",
                34.03, -5.0, 15, MeterStatus.ACTIVE);
    }

    @Test
    void listsMetersAsAStablePage() throws Exception {
        when(meterService.search(eq(MeterType.WATER), eq(null), eq("tazi"), any()))
                .thenReturn(new PageImpl<>(List.of(meter()), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/meters").param("type", "WATER").param("search", "tazi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].serial").value("CEAC-E-000123"))
                .andExpect(jsonPath("$.content[0].clientName").value("Foyer Alaoui"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void rejectsAPageSizeAbove1000() throws Exception {
        mvc.perform(get("/api/meters").param("size", "5000"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(meterService);
    }

    @Test
    void rejectsAnUnknownTypeFilter() throws Exception {
        mvc.perform(get("/api/meters").param("type", "GAS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Valeur invalide pour le paramètre « type »."));
    }

    @Test
    void createsAMeterAndReturnsItsLocation() throws Exception {
        when(meterService.create(any())).thenReturn(meter());

        mvc.perform(post("/api/meters").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.serial").value("CEAC-E-000123"));
    }

    @Test
    void returnsFieldErrorsAsProblemDetail() throws Exception {
        String body = VALID_BODY.replace("CEAC-E-000123", "CEAC-W-000123");

        mvc.perform(post("/api/meters").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Requête invalide"))
                .andExpect(jsonPath("$.errors.serial").exists());
        verifyNoInteractions(meterService);
    }

    @Test
    void reportsMalformedJsonInFrench() throws Exception {
        mvc.perform(post("/api/meters").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Le corps de la requête est illisible ou mal formé."));
    }

    @Test
    void mapsADuplicateSerialTo409() throws Exception {
        when(meterService.create(any())).thenThrow(new ConflictException("Le numéro de série CEAC-E-000123 existe déjà."));

        mvc.perform(post("/api/meters").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Le numéro de série CEAC-E-000123 existe déjà."));
    }

    @Test
    void mapsAnUnknownMeterTo404() throws Exception {
        UUID id = UUID.randomUUID();
        when(meterService.get(id)).thenThrow(new NotFoundException("Ce compteur n’existe pas."));

        mvc.perform(get("/api/meters/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Ce compteur n’existe pas."));
    }
}
