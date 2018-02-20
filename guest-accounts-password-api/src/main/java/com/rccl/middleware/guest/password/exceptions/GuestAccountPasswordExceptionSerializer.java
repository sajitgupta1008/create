package com.rccl.middleware.guest.password.exceptions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbend.lagom.javadsl.api.deser.ExceptionSerializer;
import com.lightbend.lagom.javadsl.api.deser.RawExceptionMessage;
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.lightbend.lagom.javadsl.jackson.JacksonExceptionSerializer;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;
import play.Environment;

import java.util.Collection;

import static com.rccl.middleware.guest.password.exceptions.GuestPasswordErrorCodeConstants.ACCOUNT_LOCKOUT;
import static com.rccl.middleware.guest.password.exceptions.GuestPasswordErrorCodeConstants.CONSTRAINT_VIOLATION;
import static com.rccl.middleware.guest.password.exceptions.GuestPasswordErrorCodeConstants.INVALID_SECURITY_QUESTION;
import static com.rccl.middleware.guest.password.exceptions.GuestPasswordErrorCodeConstants.INVALID_TOKEN;
import static com.rccl.middleware.guest.password.exceptions.GuestPasswordErrorCodeConstants.RECORD_NOT_FOUND;
import static com.rccl.middleware.guest.password.exceptions.GuestPasswordErrorCodeConstants.REUSE;
import static com.rccl.middleware.guest.password.exceptions.GuestPasswordErrorCodeConstants.UNKNOWN_ERROR;

public class GuestAccountPasswordExceptionSerializer implements ExceptionSerializer {
    
    public static final GuestAccountPasswordExceptionSerializer INSTANCE =
            new GuestAccountPasswordExceptionSerializer();
    
    private static final JacksonExceptionSerializer SERIALIZER = new JacksonExceptionSerializer(Environment.simple());
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    @Override
    public RawExceptionMessage serialize(Throwable exception, Collection<MessageProtocol> accept) {
        return SERIALIZER.serialize(exception, accept);
    }
    
    @Override
    public Throwable deserialize(RawExceptionMessage message) {
        try {
            JsonNode errorMsg = OBJECT_MAPPER.readValue(message.messageAsText(), JsonNode.class);
            JsonNode errorCode = errorMsg.findValue("errorCode");
            if (errorCode != null) {
                String internalMessage = errorMsg.findValue("internalMessage") != null
                        ? errorMsg.findValue("internalMessage").asText() : null;
                switch (errorCode.asText()) {
                    case ACCOUNT_LOCKOUT:
                        return new GuestAccountLockedException();
                    case RECORD_NOT_FOUND:
                        return new GuestNotFoundException();
                    case INVALID_TOKEN:
                        return new InvalidPasswordTokenException();
                    case REUSE:
                        return new InvalidPasswordException();
                    case INVALID_SECURITY_QUESTION:
                        return new InvalidSecurityQuestionAndAnswerException();
                    case CONSTRAINT_VIOLATION:
                    case UNKNOWN_ERROR:
                    default:
                        return new MiddlewareTransportException(TransportErrorCode.InternalServerError,
                                internalMessage);
                    
                }
            }
        } catch (Exception e) {
            return new MiddlewareTransportException(TransportErrorCode.InternalServerError, e.getMessage());
        }
        
        return new MiddlewareTransportException(TransportErrorCode.InternalServerError, "An error occurred.");
    }
}
