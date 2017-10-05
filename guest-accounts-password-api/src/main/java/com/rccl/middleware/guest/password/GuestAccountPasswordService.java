package com.rccl.middleware.guest.password;

import akka.NotUsed;
import com.fasterxml.jackson.databind.JsonNode;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.rccl.middleware.common.response.ResponseBody;
import com.typesafe.config.ConfigFactory;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.restCall;
import static com.lightbend.lagom.javadsl.api.Service.topic;
import static com.lightbend.lagom.javadsl.api.transport.Method.GET;
import static com.lightbend.lagom.javadsl.api.transport.Method.POST;
import static com.lightbend.lagom.javadsl.api.transport.Method.PUT;

public interface GuestAccountPasswordService extends Service {
    
    String KAFKA_TOPIC_NAME = ConfigFactory.load().getString("kafka.topic.name");
    
    ServiceCall<ForgotPassword, ResponseBody> forgotPassword(String email);
    
    ServiceCall<ForgotPasswordToken, ResponseBody> validateForgotPasswordToken();
    
    ServiceCall<PasswordInformation, ResponseBody<JsonNode>> updatePassword();
    
    Topic<EmailNotification> emailNotificationTopic();
    
    ServiceCall<NotUsed, String> healthCheck();
    
    @Override
    default Descriptor descriptor() {
        return named("guestAccountsPassword").withCalls(
                restCall(POST, "/guestAccounts/:email/forgotPassword", this::forgotPassword),
                restCall(POST, "/guestAccounts/forgotPassword/tokenValidation",
                        this::validateForgotPasswordToken),
                restCall(PUT, "/guestAccounts/password", this::updatePassword),
                restCall(GET, "/guestAccounts/health", this::healthCheck))
                .withTopics(
                        topic(KAFKA_TOPIC_NAME, this::emailNotificationTopic)
                )
                .withAutoAcl(true);
    }
}
