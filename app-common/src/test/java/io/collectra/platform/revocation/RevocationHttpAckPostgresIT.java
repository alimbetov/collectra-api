package io.collectra.platform.revocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
    "app-common.security.issuer-uri=https://issuer.example/realms/shared",
    "app-common.security.jwk-set-uri=https://issuer.example/keys",
    "app-common.security.audience=app-common",
    "app-common.revocation.reconcile-enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class RevocationHttpAckPostgresIT {
    @Container static final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", pg::getJdbcUrl);
        registry.add("spring.datasource.username", pg::getUsername);
        registry.add("spring.datasource.password", pg::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcClient db;
    @MockBean JwtDecoder decoder;

    private Jwt token(String client, String scopes) {
        Instant now=Instant.now();
        return Jwt.withTokenValue("test-"+UUID.randomUUID())
            .header("alg","RS256")
            .issuer("https://issuer.example/realms/shared")
            .subject("service-"+client)
            .issuedAt(now).expiresAt(now.plusSeconds(300))
            .claim("azp",client)
            .claim("aud",List.of("app-common"))
            .claim("scope",scopes)
            .build();
    }

    @Test
    void authorizedHttpAckIsBoundToCallerAndOldBindingCannotEnforce() throws Exception {
        String subject="http-ack-"+UUID.randomUUID();
        String issuer="https://issuer.example/realms/shared";
        db.sql("UPDATE revocation_consumers SET enabled=true,oauth_client_id='collectra-http' WHERE consumer_id='collectra-api'").update();
        db.sql("INSERT INTO subject_access_state(issuer,subject_id,state,version) VALUES (:iss,:sub,'BLOCKED',2)")
            .param("iss",issuer).param("sub",subject).update();
        when(decoder.decode(anyString())).thenReturn(token("collectra-http","revocations.read revocations.ack"));
        mvc.perform(get("/api/v1/internal/revocations/{subject}",subject)
            .param("issuer",issuer).header("Authorization","Bearer mock"))
            .andExpect(status().isOk());
        String body="{\"issuer\":\""+issuer+"\",\"subject\":\""+subject+"\",\"version\":2}";
        mvc.perform(post("/api/v1/internal/revocations/ack")
            .header("Authorization","Bearer mock").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isAccepted());
        long oldEpoch=db.sql("SELECT binding_epoch FROM revocation_acks WHERE consumer_id='collectra-api' AND issuer=:iss AND subject_id=:sub")
            .param("iss",issuer).param("sub",subject).query(Long.class).single();
        db.sql("UPDATE revocation_consumers SET oauth_client_id='collectra-rotated' WHERE consumer_id='collectra-api'").update();
        long currentEpoch=db.sql("SELECT binding_epoch FROM revocation_consumers WHERE consumer_id='collectra-api'")
            .query(Long.class).single();
        assertThat(currentEpoch).isGreaterThan(oldEpoch);
        mvc.perform(post("/api/v1/internal/revocations/ack")
            .header("Authorization","Bearer mock").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        assertThat(db.sql("SELECT binding_epoch FROM revocation_acks WHERE consumer_id='collectra-api' AND issuer=:iss AND subject_id=:sub")
            .param("iss",issuer).param("sub",subject).query(Long.class).single()).isEqualTo(oldEpoch);
    }
}
