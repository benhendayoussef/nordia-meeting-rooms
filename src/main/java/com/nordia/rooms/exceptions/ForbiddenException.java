package com.nordia.rooms.exceptions;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends BaseServiceException {
    public ForbiddenException(String code, String message) {
        super(code, message, HttpStatus.FORBIDDEN);
    }
}
