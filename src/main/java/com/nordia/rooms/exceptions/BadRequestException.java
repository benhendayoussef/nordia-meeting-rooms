package com.nordia.rooms.exceptions;

import org.springframework.http.HttpStatus;

public class BadRequestException extends BaseServiceException {
    public BadRequestException(String code, String message) {
        super(code, message, HttpStatus.BAD_REQUEST);
    }
}
