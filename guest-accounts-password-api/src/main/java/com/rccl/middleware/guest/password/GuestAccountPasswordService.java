package com.rccl.middleware.guest.password;

import akka.NotUsed;
import com.fasterxml.jackson.databind.JsonNode;
import com.lightbend.lagom.javadsl.api.CircuitBreaker;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.rccl.middleware.common.response.ResponseBody;
import com.rccl.middleware.guest.password.akka.ActorSystemHealth;
import com.rccl.middleware.guest.password.email.EmailNotification;
import com.rccl.middleware.guest.password.exceptions.GuestAccountPasswordExceptionSerializer;
import com.typesafe.config.ConfigFactory;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.restCall;
import static com.lightbend.lagom.javadsl.api.Service.topic;
import static com.lightbend.lagom.javadsl.api.transport.Method.GET;
import static com.lightbend.lagom.javadsl.api.transport.Method.POST;
import static com.lightbend.lagom.javadsl.api.transport.Method.PUT;

public interface GuestAccountPasswordService extends Service {
    
    String NOTIFICATIONS_KAFKA_TOPIC = ConfigFactory.load().getString("kafka.notifications.topic.name");
    
    ServiceCall<ForgotPassword, ResponseBody> forgotPassword(String email);
    
    ServiceCall<ForgotPasswordToken, ResponseBody> validateForgotPasswordToken();
    
    ServiceCall<PasswordInformation, ResponseBody<JsonNode>> updatePassword();
    
    ServiceCall<NotUsed, ResponseBody<ActorSystemHealth>> akkaClusterHealthCheck();
    
    Topic<EmailNotification> emailNotificationTopic();
    
    @Override
    default Descriptor descriptor() {
        return named("guest_accounts_password")
                .withCalls(
                        restCall(POST, "/guestAccounts/:email/forgotPassword", this::forgotPassword),
                        restCall(POST, "/guestAccounts/forgotPassword/tokenValidation",
                                this::validateForgotPasswordToken),
                        restCall(PUT, "/guestAccounts/password", this::updatePassword),
                        restCall(GET, "/akka-cluster/health", this::akkaClusterHealthCheck)
                )
                .withTopics(
                        topic(NOTIFICATIONS_KAFKA_TOPIC, this::emailNotificationTopic)
                )
                .withCircuitBreaker(CircuitBreaker.identifiedBy("guest_accounts_password"))
                .withExceptionSerializer(GuestAccountPasswordExceptionSerializer.INSTANCE)
                .withAutoAcl(true);
    }
}
