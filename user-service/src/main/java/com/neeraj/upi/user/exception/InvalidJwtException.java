package com.neeraj.upi.user.exception;

import com.neeraj.upi.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class InvalidJwtException extends BaseException {
    public InvalidJwtException(String message) {
        super("Invalid JWT ",message, HttpStatus.UNAUTHORIZED);
    }
}
