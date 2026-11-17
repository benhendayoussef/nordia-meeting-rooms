package com.nordia.rooms.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

import com.nordia.rooms.model.Room;
import com.nordia.rooms.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
public class RoomDataInitializer implements ApplicationRunner {

    private static final TypeReference<List<Room>> ROOM_LIST = new TypeReference<>() {
    };

    private final RoomRepository rooms;
    private final JsonMapper jsonMapper;
    private final Resource source;

    public RoomDataInitializer(RoomRepository rooms, JsonMapper jsonMapper,
                      @Value("classpath:data/rooms.json") Resource source) {
        this.rooms = rooms;
        this.jsonMapper = jsonMapper;
        this.source = source;
    }

    @Override
    public void run(ApplicationArguments args) {
        load();
    }

    // Rooms that are no longer in the file stay in the table: existing bookings may reference them.
    public void load() {
        try (InputStream in = source.getInputStream()) {
            jsonMapper.readValue(in, ROOM_LIST).forEach(rooms::upsert);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read rooms from " + source, e);
        }
    }
}
