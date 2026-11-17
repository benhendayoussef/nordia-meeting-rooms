package com.nordia.rooms.controller;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoomControllerTest {

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
    void lists_all_six_rooms_when_no_capacity_is_given() throws Exception {
        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
    }

    @Test
    void lists_rooms_sorted_by_name() throws Exception {
        mockMvc.perform(get("/api/rooms"))
                .andExpect(jsonPath("$[*].name").value(
                        contains("Aurora", "Borealis", "Cirrus", "Delta", "Equinox", "Fjord")));
    }

    @Test
    void returns_only_rooms_at_or_above_the_requested_capacity() throws Exception {
        mockMvc.perform(get("/api/rooms").param("minCapacity", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(contains("Cirrus", "Delta", "Equinox", "Fjord")))
                .andExpect(jsonPath("$[*].capacity").value(everyItem(greaterThanOrEqualTo(8))));
    }

    @Test
    void returns_an_empty_list_when_no_room_is_large_enough() throws Exception {
        mockMvc.perform(get("/api/rooms").param("minCapacity", "21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void includes_floor_and_equipment_in_each_room() throws Exception {
        mockMvc.perform(get("/api/rooms").param("minCapacity", "20"))
                .andExpect(jsonPath("$[0].name").value("Fjord"))
                .andExpect(jsonPath("$[0].floor").value(3))
                .andExpect(jsonPath("$[0].equipment[0]").value("projector"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "2.5"})
    void refuses_a_capacity_that_is_not_a_positive_integer(String minCapacity) throws Exception {
        mockMvc.perform(get("/api/rooms").param("minCapacity", minCapacity))
                .andExpect(status().isBadRequest());
    }
}
