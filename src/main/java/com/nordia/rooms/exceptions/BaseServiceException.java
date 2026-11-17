package com.nordia.rooms.exceptions;

import org.springframework.http.HttpStatus;

public abstract class BaseServiceException extends RuntimeException {
    private final String code;
    private final HttpStatus httpStatus;

    public BaseServiceException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

}
