package com.nordia.rooms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MeetingRoomsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeetingRoomsApplication.class, args);
    }
}
