package com.neeraj.upi.user.exception;

import com.neeraj.upi.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class UserInactiveException extends BaseException {
    public UserInactiveException(String message) {
        super("USER_INACTIVE", message, HttpStatus.FORBIDDEN);
    }
}
