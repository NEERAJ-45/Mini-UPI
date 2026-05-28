package com.neeraj.upi.user.exception;

import com.neeraj.upi.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class OutboxSerializationException extends BaseException      {
    
    public OutboxSerializationException(Throwable cause) {
        super("OUTBOX_SERIALIZATION_FAILED",
              "Failed to serialize outbox event payload",
              HttpStatus.INTERNAL_SERVER_ERROR,
              cause);
    }
}
 