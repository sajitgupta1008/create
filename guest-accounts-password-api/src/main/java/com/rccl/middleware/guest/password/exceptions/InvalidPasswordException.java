package com.rccl.middleware.guest.password.exceptions;

import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;

public class InvalidPasswordException extends MiddlewareTransportException {
    
    public static final String DEFAULT_MESSAGE = "The password must be between 8 and 32 characters, inclusive, "
            + "with at least one (1) letter and one (1) number.";
    
    public static final String REUSE_ERROR = "Passwords may not be reused.";
    
    public InvalidPasswordException() {
        super(TransportErrorCode.fromHttp(422), DEFAULT_MESSAGE);
    }
    
    public InvalidPasswordException(String message) {
        super(TransportErrorCode.fromHttp(422), message);
    }
}
