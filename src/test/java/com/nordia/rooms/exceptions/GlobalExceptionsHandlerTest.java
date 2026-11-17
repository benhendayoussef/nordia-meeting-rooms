package com.nordia.rooms.exceptions;

import com.nordia.rooms.controller.RoomController;
import com.nordia.rooms.service.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomController.class)
class GlobalExceptionsHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RoomService roomService;

    @Test
    void hides_the_details_of_an_unexpected_exception() throws Exception {
        when(roomService.findByMinCapacity(anyInt()))
                .thenThrow(new IllegalStateException("SQLITE_BUSY: database is locked"));

        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.instance").value("/api/rooms"))
                .andExpect(content().string(not(containsString("SQLITE_BUSY"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void returns_a_business_exception_with_its_own_status_and_code() throws Exception {
        when(roomService.findByMinCapacity(anyInt())).thenThrow(ResourceNotFoundException.roomNotFound(9));

        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Room 9 does not exist."));
    }

    @Test
    void answers_405_when_the_method_is_not_supported() throws Exception {
        mockMvc.perform(post("/api/rooms"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void answers_404_with_a_problem_body_for_an_unknown_path() throws Exception {
        mockMvc.perform(get("/api/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
