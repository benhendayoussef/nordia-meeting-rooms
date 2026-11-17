package com.nordia.rooms.exceptions;

import org.springframework.http.HttpStatus;

public class UnprocessableEntityException extends BaseServiceException {
    public UnprocessableEntityException(String code, String message) {
        super(code, message, HttpStatus.UNPROCESSABLE_CONTENT);
    }
}
