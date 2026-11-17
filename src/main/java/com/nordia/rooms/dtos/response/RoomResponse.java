package com.nordia.rooms.dtos.response;

import java.util.List;

import com.nordia.rooms.model.Room;

public record RoomResponse(int id, String name, int capacity, int floor, List<String> equipment) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(room.id(), room.name(), room.capacity(), room.floor(), room.equipment());
    }
}
