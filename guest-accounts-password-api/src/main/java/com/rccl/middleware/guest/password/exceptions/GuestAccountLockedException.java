package com.rccl.middleware.guest.password.exceptions;

import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;

import static com.rccl.middleware.guest.password.exceptions.GuestPasswordErrorCodeConstants.ACCOUNT_LOCKOUT;

public class GuestAccountLockedException extends MiddlewareTransportException {
    private static final String DEFAULT_MESSAGE = "The account is currently locked.";
    
    public GuestAccountLockedException() {
        super(TransportErrorCode.fromHttp(401), DEFAULT_MESSAGE, ACCOUNT_LOCKOUT);
    }
}
