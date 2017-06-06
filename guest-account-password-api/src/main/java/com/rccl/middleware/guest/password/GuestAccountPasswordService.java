package com.rccl.middleware.guest.password;

import akka.NotUsed;
import com.fasterxml.jackson.databind.JsonNode;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.restCall;
import static com.lightbend.lagom.javadsl.api.Service.topic;
import static com.lightbend.lagom.javadsl.api.transport.Method.POST;
import static com.lightbend.lagom.javadsl.api.transport.Method.PUT;

public interface GuestAccountPasswordService extends Service {
    
    String NOTIFICATIONS_TOPIC = "notifications.email";
    
    ServiceCall<NotUsed, NotUsed> forgotPassword(String email);
    
    ServiceCall<PasswordInformation, JsonNode> updatePassword(String email);
    
    Topic<EmailNotification> emailNotificationTopic();
    
    @Override
    default Descriptor descriptor() {
        return named("guestAccountsPassword").withCalls(
                restCall(POST, "/v1/guestAccounts/:emailId/forgotPassword", this::forgotPassword),
                restCall(PUT, "/v1/guestAccounts/:emailId/password", this::updatePassword))
                .publishing(
                        topic(NOTIFICATIONS_TOPIC, this::emailNotificationTopic)
                )
                .withAutoAcl(true);
    }
}
