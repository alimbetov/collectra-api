package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class FoundationSmokeIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void actuatorHealthIsAvailableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openApiContractIsAvailableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());
    }

    @Test
    void protectedEndpointRejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/v1/identity/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointAppliesBeanValidation() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/tenants/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isMap());
    }

    @Test
    void liquibaseCreatedFoundationTables() {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                          from information_schema.tables
                         where table_schema = 'public'
                           and table_name in ('tenants', 'user_accounts', 'outbox_events',
                               'tenant_memberships', 'roles', 'permissions', 'service_clients',
                               'otp_challenges', 'security_audit_events')
                        """,
                        Integer.class);

        assertThat(count).isEqualTo(9);
    }
}
