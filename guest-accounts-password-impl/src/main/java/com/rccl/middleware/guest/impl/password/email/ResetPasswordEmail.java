package com.rccl.middleware.guest.impl.password.email;

import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.rccl.middleware.aem.api.AemService;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;
import com.rccl.middleware.common.logging.RcclLoggerFactory;
import com.rccl.middleware.guest.password.EmailNotification;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.CompletionStage;

public class ResetPasswordEmail {
    
    private static final Logger LOGGER = RcclLoggerFactory.getLogger(ResetPasswordEmail.class);
    
    private AemService aemService;
    
    private PersistentEntityRegistry persistentEntityRegistry;
    
    public ResetPasswordEmail(AemService aemService,
                              PersistentEntityRegistry persistentEntityRegistry) {
        this.aemService = aemService;
        this.persistentEntityRegistry = persistentEntityRegistry;
    }
    
    public void send(String email, String firstName, String resetPasswordUrl) {
        this.getResetPasswordEmailTemplate()
                .thenAccept(aemTemplateResponse -> {
                    String content = this.getPopulatedEmailTemplate(aemTemplateResponse, email, firstName, resetPasswordUrl);
                    String sender = "notifications@rccl.com";
                    String subject = "Reset your My Cruises password";
                    
                    EmailNotification en = EmailNotification.builder()
                            .content(content)
                            .recipient(email)
                            .sender(sender)
                            .subject(subject)
                            .build();
                    
                    this.sendToTopic(en);
                });
    }
    
    private CompletionStage<JsonNode> getResetPasswordEmailTemplate() {
        return aemService.getResetPasswordEmail()
                .invoke()
                .exceptionally(throwable -> {
                    LOGGER.error("#getResetPasswordEmailTemplate:", throwable);
                    throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                });
    }
    
    private String getPopulatedEmailTemplate(JsonNode aemTemplateResponse, String email, String firstName, String resetPasswordUrl) {
        return StringUtils.replaceEach(
                aemTemplateResponse.findValue("data").get("text").asText(),
                new String[]{"<first name>", "<guest username/email>", "<link to reset>"},
                new String[]{firstName, email, resetPasswordUrl});
    }
    
    private void sendToTopic(EmailNotification emailNotification) {
        persistentEntityRegistry
                .refFor(EmailNotificationEntity.class, emailNotification.getRecipient())
                .ask(new EmailNotificationCommand.SendEmailNotification(emailNotification));
    }
}
