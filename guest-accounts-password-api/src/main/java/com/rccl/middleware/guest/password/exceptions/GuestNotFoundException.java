package com.rccl.middleware.guest.password.exceptions;

import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;

import static com.rccl.middleware.guest.password.exceptions.GuestPasswordErrorCodeConstants.RECORD_NOT_FOUND;

public class GuestNotFoundException extends MiddlewareTransportException {
    
    private static final String DEFAULT_MESSAGE = "User with details provided was not found.";
    
    public GuestNotFoundException() {
        super(TransportErrorCode.fromHttp(404), DEFAULT_MESSAGE, RECORD_NOT_FOUND);
    }
}
