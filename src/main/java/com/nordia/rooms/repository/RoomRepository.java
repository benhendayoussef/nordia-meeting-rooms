package com.nordia.rooms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.nordia.rooms.model.Room;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class RoomRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    public RoomRepository(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    public List<Room> findByMinCapacity(int minCapacity) {
        return jdbc.sql("""
                        SELECT id, name, capacity, floor, equipment
                        FROM room
                        WHERE capacity >= :minCapacity
                        ORDER BY name
                        """)
                .param("minCapacity", minCapacity)
                .query(this::mapRoom)
                .list();
    }

    public void upsert(Room room) {
        jdbc.sql("""
                        INSERT INTO room (id, name, capacity, floor, equipment)
                        VALUES (:id, :name, :capacity, :floor, :equipment)
                        ON CONFLICT(id) DO UPDATE SET
                            name = excluded.name,
                            capacity = excluded.capacity,
                            floor = excluded.floor,
                            equipment = excluded.equipment
                        """)
                .param("id", room.id())
                .param("name", room.name())
                .param("capacity", room.capacity())
                .param("floor", room.floor())
                .param("equipment", jsonMapper.writeValueAsString(room.equipment()))
                .update();
    }

    public int count() {
        return jdbc.sql("SELECT COUNT(*) FROM room").query(Integer.class).single();
    }

    private Room mapRoom(ResultSet rs, int rowNum) throws SQLException {
        return new Room(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getInt("capacity"),
                rs.getInt("floor"),
                jsonMapper.readValue(rs.getString("equipment"), STRING_LIST));
    }
}
