package com.nordia.rooms.controller;

import java.util.List;

import com.nordia.rooms.dtos.response.RoomResponse;
import com.nordia.rooms.service.RoomService;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public List<RoomResponse> list(@RequestParam(defaultValue = "1") @Positive int minCapacity) {
        return roomService.findByMinCapacity(minCapacity).stream()
                .map(RoomResponse::from)
                .toList();
    }
}
