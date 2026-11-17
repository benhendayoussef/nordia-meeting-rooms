package com.nordia.rooms.service;

import java.util.List;

import com.nordia.rooms.exceptions.ResourceNotFoundException;
import com.nordia.rooms.model.Room;
import com.nordia.rooms.repository.RoomRepository;
import org.springframework.stereotype.Service;

@Service
public class RoomService {

    private final RoomRepository rooms;

    public RoomService(RoomRepository rooms) {
        this.rooms = rooms;
    }

    public List<Room> findByMinCapacity(int minCapacity) {
        return rooms.findByMinCapacity(minCapacity);
    }

    public Room getById(int id) {
        return rooms.findById(id).orElseThrow(() -> ResourceNotFoundException.roomNotFound(id));
    }
}
