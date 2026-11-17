package com.nordia.rooms.config;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OpenApiDocsTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("test.db")
                + "?foreign_keys=on&transaction_mode=IMMEDIATE&busy_timeout=5000");
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void serves_the_api_description() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Nordia Meeting Rooms API"))
                .andExpect(jsonPath("$.paths").value(hasKey("/api/rooms")))
                .andExpect(jsonPath("$.paths").value(hasKey("/api/rooms/{roomId}/bookings")))
                .andExpect(jsonPath("$.paths").value(hasKey("/api/bookings")))
                .andExpect(jsonPath("$.paths").value(hasKey("/api/bookings/{id}")));
    }

    @Test
    void describes_error_responses_as_problem_details() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/bookings'].post.responses.409.content['application/problem+json']"
                        + ".schema.$ref").value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/bookings'].post.responses.422").exists())
                .andExpect(jsonPath("$.paths['/api/bookings/{id}'].delete.responses.403").exists())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties").value(hasKey("code")))
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties").value(hasKey("errors")));
    }
}
