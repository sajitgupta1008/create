package com.rccl.middleware.guest.password.exceptions;

import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;

public class InvalidPasswordException extends MiddlewareTransportException {
    
    public InvalidPasswordException() {
        super(TransportErrorCode.fromHttp(422), "The password must be between 7 and 32 characters, inclusive, " +
                "with at one (1) special character or a number.");
    }
}
