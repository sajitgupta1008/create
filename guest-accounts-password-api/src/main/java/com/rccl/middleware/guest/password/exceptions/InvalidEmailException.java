package com.rccl.middleware.guest.password.exceptions;

import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;

import static com.rccl.middleware.guest.password.exceptions.GuestPasswordErrorCodeConstants.CONSTRAINT_VIOLATION;

public class InvalidEmailException extends MiddlewareTransportException {
    
    public InvalidEmailException() {
        super(TransportErrorCode.fromHttp(422), "The email is invalidly formatted.", CONSTRAINT_VIOLATION);
    }
}
