package com.rccl.middleware.guest.password.exceptions;

import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;

public class InvalidPasswordException extends MiddlewareTransportException {
    
    public InvalidPasswordException() {
        super(TransportErrorCode.fromHttp(422), "The password must be between 7 and 10 characters, inclusive, " +
                "with at least three (3) letters, two (2) numbers, and one (1) special character.");
    }
}
