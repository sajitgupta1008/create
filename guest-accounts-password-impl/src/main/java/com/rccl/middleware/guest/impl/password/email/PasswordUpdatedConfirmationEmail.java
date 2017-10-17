package com.rccl.middleware.guest.impl.password.email;

import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.rccl.middleware.aem.api.AemService;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;
import com.rccl.middleware.common.logging.RcclLoggerFactory;
import com.rccl.middleware.guest.password.EmailNotification;
import com.rccl.middleware.guest.password.PasswordInformation;

import java.util.concurrent.CompletionStage;

public class PasswordUpdatedConfirmationEmail {
    
    private static final Logger LOGGER = RcclLoggerFactory.getLogger(ResetPasswordEmail.class);
    
    private AemService aemService;
    
    private PersistentEntityRegistry persistentEntityRegistry;
    
    public PasswordUpdatedConfirmationEmail(AemService aemService,
                                            PersistentEntityRegistry persistentEntityRegistry) {
        this.aemService = aemService;
        this.persistentEntityRegistry = persistentEntityRegistry;
    }
    
    public void send(PasswordInformation pi) {
        this.getPasswordUpdatedConfirmationEmailTemplate()
                .thenAccept(aemTemplateResponse -> {
                    String content = this.getPopulatedEmailTemplate(aemTemplateResponse, pi.getEmail());
                    String sender = "notifications@rccl.com";
                    String subject = aemTemplateResponse.get("subject").asText();
                    
                    EmailNotification en = EmailNotification.builder()
                            .content(content)
                            .recipient(pi.getEmail())
                            .sender(sender)
                            .subject(subject)
                            .build();
                    
                    this.sendToTopic(en);
                });
    }
    
    private CompletionStage<JsonNode> getPasswordUpdatedConfirmationEmailTemplate() {
        // TODO: Update to retrieve the password updated confirmation email template
        // TODO: aemService.getPasswordUpdatedConfirmationEmailTemplate()
        return aemService.getResetPasswordEmail()
                .invoke()
                .exceptionally(throwable -> {
                    LOGGER.error("#getPasswordUpdatedConfirmationEmailTemplate:", throwable);
                    throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                });
    }
    
    private String getPopulatedEmailTemplate(JsonNode aemTemplateResponse, String email) {
        // TODO: Add logic for replacing values.
        return null;
    }
    
    private void sendToTopic(EmailNotification emailNotification) {
        persistentEntityRegistry
                .refFor(EmailNotificationEntity.class, emailNotification.getRecipient())
                .ask(new EmailNotificationCommand.SendEmailNotification(emailNotification));
    }
}
