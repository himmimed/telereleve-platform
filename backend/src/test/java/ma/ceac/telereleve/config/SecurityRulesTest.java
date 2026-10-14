package ma.ceac.telereleve.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.ServletException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import ma.ceac.telereleve.alert.AlertController;
import ma.ceac.telereleve.alert.AlertService;
import ma.ceac.telereleve.anomaly.RuleConfigController;
import ma.ceac.telereleve.anomaly.RuleConfigService;
import ma.ceac.telereleve.dashboard.DashboardController;
import ma.ceac.telereleve.dashboard.DashboardService;
import ma.ceac.telereleve.meter.MeterController;
import ma.ceac.telereleve.meter.MeterService;
import ma.ceac.telereleve.reading.ConsumptionService;
import ma.ceac.telereleve.reading.ReadingController;
import ma.ceac.telereleve.reading.ReadingQueryService;
import ma.ceac.telereleve.user.AppUserRepository;
import ma.ceac.telereleve.user.AuthController;
import ma.ceac.telereleve.user.AuthService;
import ma.ceac.telereleve.user.AuthenticatedUser;
import ma.ceac.telereleve.user.Role;
import ma.ceac.telereleve.user.UserController;
import ma.ceac.telereleve.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * API security slice (MockMvc): the real SecurityConfig in front of the real controllers, services
 * mocked. Checks the route permissions of the specification for every role. Services return nothing
 * here, so an allowed call may fail later (500); what matters is that it is not 401 / 403.
 */
@WebMvcTest(controllers = {MeterController.class, ReadingController.class, AlertController.class,
        RuleConfigController.class, UserController.class, AuthController.class, DashboardController.class})
@Import(SecurityConfig.class)
class SecurityRulesTest {

    private static final String ID = UUID.randomUUID().toString();
    private static final String METER_BODY = """
            {"serial":"CEAC-E-000123","type":"ELECTRICITY","clientName":"Foyer","address":"Fès",
             "latitude":34.0,"longitude":-5.0,"readingIntervalMin":15,"status":"ACTIVE"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean private MeterService meterService;
    @MockitoBean private ReadingQueryService readingQueryService;
    @MockitoBean private ConsumptionService consumptionService;
    @MockitoBean private AlertService alertService;
    @MockitoBean private RuleConfigService ruleConfigService;
    @MockitoBean private UserService userService;
    @MockitoBean private AuthService authService;
    @MockitoBean private AppUserRepository appUserRepository;
    @MockitoBean private DashboardService dashboardService;

    private static UsernamePasswordAuthenticationToken as(Role role) {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(UUID.randomUUID(), role.name().toLowerCase(), "Test", role), null,
                List.of(new SimpleGrantedAuthority(role.authority())));
    }

    private static MockHttpServletRequestBuilder call(HttpMethod method, String url, String body) {
        MockHttpServletRequestBuilder request = request(method, url);
        return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // ------------------------------------------------------------------ cases of the specification

    @Test
    void aTechnicianCannotCreateAMeter() throws Exception {
        mvc.perform(call(HttpMethod.POST, "/api/meters", METER_BODY).with(authentication(as(Role.TECHNICIAN))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Vous n’avez pas les droits nécessaires pour cette action."));
    }

    @Test
    void anUnauthenticatedCallIsRejected() throws Exception {
        mvc.perform(call(HttpMethod.GET, "/api/meters", null))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Non authentifié"));
    }

    @Test
    void anInvalidTokenIsRejected() throws Exception {
        mvc.perform(call(HttpMethod.GET, "/api/alerts", null).header("Authorization", "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginIsPublic() throws Exception {
        mvc.perform(call(HttpMethod.POST, "/api/auth/login", "{\"username\":\"a\",\"password\":\"b\"}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
    }

    @Test
    void unknownApiRoutesAreDeniedByDefault() throws Exception {
        mvc.perform(call(HttpMethod.GET, "/api/secret", null).with(authentication(as(Role.ADMIN))))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ full matrix

    /** endpoint, then whether ADMIN, OPERATOR, TECHNICIAN may call it (specification, "REST API"). */
    static Stream<Arguments> matrix() {
        return Stream.of(
                row(HttpMethod.GET, "/api/meters", null, true, true, false),
                row(HttpMethod.POST, "/api/meters", METER_BODY, true, false, false),
                row(HttpMethod.PUT, "/api/meters/" + ID, METER_BODY, true, false, false),
                row(HttpMethod.GET, "/api/meters/" + ID, null, true, true, false),
                row(HttpMethod.GET, "/api/meters/" + ID + "/consumption", null, true, true, false),
                row(HttpMethod.GET, "/api/meters/" + ID + "/export.csv", null, true, true, false),
                row(HttpMethod.GET, "/api/alerts", null, true, true, true),
                row(HttpMethod.PATCH, "/api/alerts/" + ID, "{\"status\":\"RESOLVED\"}", false, true, true),
                row(HttpMethod.GET, "/api/rules", null, true, false, false),
                row(HttpMethod.PUT, "/api/rules/A5", "{\"enabled\":true,\"threshold\":10}", true, false, false),
                row(HttpMethod.GET, "/api/users", null, true, false, false),
                row(HttpMethod.POST, "/api/users", "{}", true, false, false),
                row(HttpMethod.DELETE, "/api/users/" + ID, null, true, false, false),
                row(HttpMethod.GET, "/api/users/technicians", null, true, true, false),
                row(HttpMethod.GET, "/api/meters/map", null, true, true, true),
                row(HttpMethod.GET, "/api/dashboard/stats", null, true, true, true),
                row(HttpMethod.GET, "/api/dashboard/consumption-trend", null, true, true, true));
    }

    private static Arguments row(HttpMethod method, String url, String body, boolean admin, boolean operator, boolean technician) {
        return Arguments.of(method, url, body, admin, operator, technician);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("matrix")
    void enforcesTheRolesOfEachEndpoint(HttpMethod method, String url, String body,
                                        boolean admin, boolean operator, boolean technician) throws Exception {
        expect(method, url, body, Role.ADMIN, admin);
        expect(method, url, body, Role.OPERATOR, operator);
        expect(method, url, body, Role.TECHNICIAN, technician);
        mvc.perform(call(method, url, body)).andExpect(status().isUnauthorized());
    }

    private void expect(HttpMethod method, String url, String body, Role role, boolean allowed) throws Exception {
        int status;
        try {
            status = mvc.perform(call(method, url, body).with(authentication(as(role)))).andReturn().getResponse().getStatus();
        } catch (ServletException reachedTheController) {
            // The mocked service returned nothing and the controller failed: the request got past security.
            status = 500;
        }
        if (allowed) {
            assertThat(status).as("%s %s as %s", method, url, role).isNotIn(401, 403);
        } else {
            assertThat(status).as("%s %s as %s", method, url, role).isEqualTo(403);
        }
    }
}
