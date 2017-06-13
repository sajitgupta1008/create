package com.rccl.middleware.guest.password.exceptions;

import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;

public class InvalidEmailException extends MiddlewareTransportException {
    
    public InvalidEmailException() {
        super(TransportErrorCode.fromHttp(422), "The email is invalidly formatted.");
    }
}
