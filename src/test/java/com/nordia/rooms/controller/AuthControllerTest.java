package com.nordia.rooms.controller;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.jayway.jsonpath.JsonPath;
import com.nordia.rooms.config.JwtProperties;
import com.nordia.rooms.config.UserDataInitializer;
import com.nordia.rooms.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthControllerTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("test.db")
                + "?foreign_keys=on&transaction_mode=IMMEDIATE&busy_timeout=5000");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    Clock clock;

    @Autowired
    UserDataInitializer userDataInitializer;

    @Test
    void logs_in_a_demo_user_with_a_token_that_expires_in_one_hour() throws Exception {
        Instant before = Instant.now();

        String body = login("alice@nordia.com", "alice123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.expiresAt").isString())
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(body, "$.accessToken");
        assertThat(jwtUtils.extractEmail(token)).isEqualTo("alice@nordia.com");
        assertThat(jwtUtils.extractExpiration(token))
                .isBetween(before.plus(Duration.ofMinutes(59)), Instant.now().plus(Duration.ofMinutes(61)));
    }

    @Test
    void ignores_case_and_spaces_in_the_login_email() throws Exception {
        String body = login("  Bob@Nordia.com ", "bob123")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(jwtUtils.extractEmail(JsonPath.read(body, "$.accessToken"))).isEqualTo("bob@nordia.com");
    }

    @Test
    void refuses_a_wrong_password() throws Exception {
        login("alice@nordia.com", "not-the-password")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password."))
                .andExpect(jsonPath("$.instance").value("/api/auth/login"));
    }

    @Test
    void refuses_an_unknown_email_in_the_same_way() throws Exception {
        login("nobody@nordia.com", "alice123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void refuses_a_login_without_a_password() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@nordia.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void creates_the_three_demo_users_with_hashed_passwords() {
        assertThat(jdbc.sql("SELECT COUNT(*) FROM app_user").query(Integer.class).single()).isEqualTo(3);
        assertThat(jdbc.sql("SELECT password_hash FROM app_user WHERE email = 'carol@nordia.com'")
                .query(String.class).single())
                .startsWith("$2")
                .isNotEqualTo("carol123");
    }

    @Test
    void creating_the_demo_users_again_does_not_duplicate_them() {
        userDataInitializer.run(null);

        assertThat(jdbc.sql("SELECT COUNT(*) FROM app_user").query(Integer.class).single()).isEqualTo(3);
    }

    @Test
    void keeps_listing_rooms_open_without_a_token() throws Exception {
        mockMvc.perform(get("/api/rooms")).andExpect(status().isOk());
        mockMvc.perform(get("/api/rooms/1/bookings").param("date", "2026-11-17")).andExpect(status().isOk());
    }

    @Test
    void refuses_to_create_a_booking_without_a_token() throws Exception {
        createBookingWith(null)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.instance").value("/api/bookings"));
    }

    @Test
    void refuses_a_token_that_is_not_valid() throws Exception {
        createBookingWith("Bearer not-a-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void refuses_a_token_signed_with_another_secret() throws Exception {
        JwtUtils stranger = new JwtUtils(new JwtProperties("a-different-secret-of-32-characters!", Duration.ofHours(1)), clock);

        createBookingWith("Bearer " + stranger.generateAccessJwtToken("alice@nordia.com"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void accepts_the_token_it_issued_on_a_protected_endpoint() throws Exception {
        String body = login("alice@nordia.com", "alice123").andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(get("/api/bookings/999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private ResultActions createBookingWith(String authorization) throws Exception {
        var request = post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":1,\"start\":\"2026-11-17T09:00:00+01:00\","
                        + "\"end\":\"2026-11-17T10:00:00+01:00\",\"subject\":\"Planning\"}");
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return mockMvc.perform(request);
    }
}
