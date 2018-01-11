package com.rccl.middleware.guest.password.exceptions;

import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;

import static com.rccl.middleware.guest.password.exceptions.GuestPasswordErrorCodeConstants.INVALID_SECURITY_QUESTION;

public class InvalidSecurityQuestionAndAnswerException extends MiddlewareTransportException {
    
    public InvalidSecurityQuestionAndAnswerException() {
        super(TransportErrorCode.fromHttp(422),
                "The security question and answer are invalid.", INVALID_SECURITY_QUESTION);
    }
}
