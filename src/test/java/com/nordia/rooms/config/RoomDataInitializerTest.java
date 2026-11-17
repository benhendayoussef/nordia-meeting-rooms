package com.nordia.rooms.config;

import java.nio.file.Path;
import java.util.List;

import com.nordia.rooms.model.Room;
import com.nordia.rooms.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoomDataInitializerTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("test.db")
                + "?foreign_keys=on&transaction_mode=IMMEDIATE&busy_timeout=5000");
    }

    @Autowired
    RoomDataInitializer loader;

    @Autowired
    RoomRepository rooms;

    @Autowired
    JsonMapper jsonMapper;

    @BeforeEach
    void restoreRoomsFromTheBundledFile() {
        loader.load();
    }

    @Test
    void loads_the_six_rooms_from_rooms_json() {
        assertThat(rooms.findByMinCapacity(1)).hasSize(6);
    }

    @Test
    void loading_twice_does_not_duplicate_rooms() {
        loader.load();
        loader.load();

        assertThat(rooms.count()).isEqualTo(6);
    }

    @Test
    void applies_a_changed_capacity_when_the_file_is_loaded_again() {
        Room aurora = rooms.findByMinCapacity(1).getFirst();
        Room larger = new Room(aurora.id(), aurora.name(), 5, aurora.floor(), aurora.equipment());

        loaderReading(larger).load();

        assertThat(rooms.count()).isEqualTo(6);
        assertThat(rooms.findByMinCapacity(1).getFirst().capacity()).isEqualTo(5);
    }

    @Test
    void keeps_rooms_that_are_missing_from_the_file() {
        Room aurora = rooms.findByMinCapacity(1).getFirst();

        loaderReading(aurora).load();

        assertThat(rooms.count()).isEqualTo(6);
    }

    private RoomDataInitializer loaderReading(Room... roomsInFile) {
        byte[] json = jsonMapper.writeValueAsBytes(List.of(roomsInFile));
        return new RoomDataInitializer(rooms, jsonMapper, new ByteArrayResource(json));
    }
}
