package com.rccl.middleware.guest.password.exceptions;

import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;

import static com.rccl.middleware.guest.password.exceptions.GuestPasswordErrorCodeConstants.INVALID_TOKEN;

public class InvalidPasswordTokenException extends MiddlewareTransportException {
    
    public InvalidPasswordTokenException() {
        super(TransportErrorCode.fromHttp(422), "Token validation failed. "
                + "The token specified is either invalid or has already expired.", INVALID_TOKEN);
    }
}
