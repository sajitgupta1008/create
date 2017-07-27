package com.rccl.middleware.guest.password.exceptions;

import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;

public class InvalidPasswordTokenException extends MiddlewareTransportException {
    
    public InvalidPasswordTokenException() {
        super(TransportErrorCode.fromHttp(422), "Token validation failed. "
                + "The token specified is either invalid or is already expired.");
    }
}
