package com.nordia.rooms.exceptions;

import org.springframework.http.HttpStatus;

public class ConflictException extends BaseServiceException {
    public ConflictException(String code, String message) {
        super(code, message, HttpStatus.CONFLICT);
    }
}
