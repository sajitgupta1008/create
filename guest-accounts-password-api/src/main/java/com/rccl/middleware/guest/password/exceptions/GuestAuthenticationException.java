package com.rccl.middleware.guest.password.exceptions;

import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.rccl.middleware.common.exceptions.MiddlewareExceptionMessage;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;

public class GuestAuthenticationException extends MiddlewareTransportException {
    
    private static final String DEFAULT_MESSAGE = "Authentication failed with the credentials provided.";
    
    public GuestAuthenticationException() {
        super(TransportErrorCode.fromHttp(401), new MiddlewareExceptionMessage(DEFAULT_MESSAGE));
    }
    
    public GuestAuthenticationException(String message) {
        super(TransportErrorCode.fromHttp(401), new MiddlewareExceptionMessage(message));
    }
}
