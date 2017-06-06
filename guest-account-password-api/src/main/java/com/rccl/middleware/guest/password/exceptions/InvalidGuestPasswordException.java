package com.rccl.middleware.guest.password.exceptions;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.rccl.middleware.common.exceptions.MiddlewareExceptionMessage;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class InvalidGuestPasswordException extends MiddlewareTransportException {
    
    public static final InvalidGuestPasswordException INVALID_PASSWORD;
    
    public static final InvalidGuestPasswordException INVALID_EMAIL;
    
    private InvalidGuestPasswordExceptionMessage invalidGuestPasswordExceptionMsg;
    
    static {
        Map<String, String> passwordError = new HashMap<>();
        passwordError.put("password", "The password must be between 7 and 10 characters, inclusive, " +
                "with at least three (3) letters and two (2) numbers.");
        INVALID_PASSWORD = new InvalidGuestPasswordException(passwordError);
    
        Map<String, String> emailError = new HashMap<>();
        passwordError.put("password", "The email is in an invalid format.");
        INVALID_EMAIL = new InvalidGuestPasswordException(emailError);
    }
    
    public InvalidGuestPasswordException(Map<String, String> validationErrors) {
        this("The request body is improper.", validationErrors);
    }
    
    public InvalidGuestPasswordException(String errorMessage, Map<String, String> validationErrors) {
        super(TransportErrorCode.fromHttp(422), new InvalidGuestPasswordExceptionMessage(errorMessage));
        
        this.invalidGuestPasswordExceptionMsg = (InvalidGuestPasswordExceptionMessage) super.exceptionMessage();
        invalidGuestPasswordExceptionMsg.setValidationErrors(validationErrors);
    }
    
    @Override
    public InvalidGuestPasswordExceptionMessage exceptionMessage() {
        return this.invalidGuestPasswordExceptionMsg;
    }
    
    public static final class InvalidGuestPasswordExceptionMessage extends MiddlewareExceptionMessage {
        private Map<String, String> validationErrors;
        
        public InvalidGuestPasswordExceptionMessage(String errorMessage) {
            super(errorMessage);
        }
        
        public Map<String, String> getValidationErrors() {
            return validationErrors;
        }
        
        public void setValidationErrors(Map<String, String> validationErrors) {
            this.validationErrors = validationErrors;
        }
    }
}
