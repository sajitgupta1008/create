package com.rccl.middleware.guest.password.exceptions;

import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.rccl.middleware.common.exceptions.MiddlewareExceptionMessage;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;

public class GuestAccountLockedException extends MiddlewareTransportException {
    private static final String DEFAULT_MESSAGE = "The account is currently locked.";
    
    public GuestAccountLockedException() {
        super(TransportErrorCode.fromHttp(401), new MiddlewareExceptionMessage(DEFAULT_MESSAGE));
    }
}
