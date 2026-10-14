package ma.ceac.telereleve.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import ma.ceac.telereleve.alert.dto.AlertUpdateRequest;
import org.junit.jupiter.api.Test;

/** "Field absent" and "field null" mean different things in a PATCH: absent = unchanged, null = unassign. */
class AlertUpdateRequestTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void aStatusOnlyUpdateLeavesTheAssignmentAlone() throws Exception {
        AlertUpdateRequest request = json.readValue("{\"status\":\"RESOLVED\"}", AlertUpdateRequest.class);

        assertThat(request.status()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(request.changesAssignment()).isFalse();
    }

    @Test
    void anExplicitNullUnassigns() throws Exception {
        AlertUpdateRequest request = json.readValue("{\"assignedTo\":null}", AlertUpdateRequest.class);

        assertThat(request.changesAssignment()).isTrue();
        assertThat(request.assignedTo()).isEmpty();
    }

    @Test
    void anIdAssigns() throws Exception {
        UUID id = UUID.randomUUID();
        AlertUpdateRequest request = json.readValue("{\"assignedTo\":\"" + id + "\"}", AlertUpdateRequest.class);

        assertThat(request.changesAssignment()).isTrue();
        assertThat(request.assignedTo()).contains(id);
    }
}
