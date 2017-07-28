package com.rccl.middleware.guest.password;

import akka.NotUsed;
import com.fasterxml.jackson.databind.JsonNode;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.typesafe.config.ConfigFactory;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.restCall;
import static com.lightbend.lagom.javadsl.api.Service.topic;
import static com.lightbend.lagom.javadsl.api.transport.Method.GET;
import static com.lightbend.lagom.javadsl.api.transport.Method.POST;
import static com.lightbend.lagom.javadsl.api.transport.Method.PUT;

public interface GuestAccountPasswordService extends Service {
    
    String KAFKA_TOPIC_NAME = ConfigFactory.load().getString("kafka.topic.name");
    
    String SHIP_CODE = ConfigFactory.load().getString("ship.code");
    
    ServiceCall<ForgotPassword, NotUsed> forgotPassword(String email);
    
    ServiceCall<ForgotPasswordToken, NotUsed> validateForgotPasswordToken();
    
    ServiceCall<PasswordInformation, JsonNode> updatePassword();
    
    Topic<EmailNotification> emailNotificationTopic();
    
    ServiceCall<NotUsed, String> healthCheck();
    
    @Override
    default Descriptor descriptor() {
        return named("guestAccountsPassword").withCalls(
                restCall(POST, "/v1/guestAccounts/:email/forgotPassword", this::forgotPassword),
                restCall(POST, "/v1/guestAccounts/forgotPassword/validation", this::validateForgotPasswordToken),
                restCall(PUT, "/v1/guestAccounts/password", this::updatePassword),
                restCall(GET, "/v1/guestAccounts/health", this::healthCheck))
                .publishing(
                        topic(KAFKA_TOPIC_NAME, this::emailNotificationTopic)
                )
                .withAutoAcl(true);
    }
}
