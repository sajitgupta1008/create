package com.rccl.middleware.guest.impl.password.email;

import ch.qos.logback.classic.Logger;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.rccl.middleware.aem.api.models.HtmlEmailTemplate;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;
import com.rccl.middleware.common.logging.RcclLoggerFactory;
import com.rccl.middleware.notifications.EmailNotification;
import com.rccl.middleware.notifications.NotificationsService;

import javax.inject.Inject;

public class NotificationsHelper {
    
    private final NotificationsService notificationsService;
    private static final Logger LOGGER = RcclLoggerFactory.getLogger(NotificationsHelper.class);
    
    @Inject
    public NotificationsHelper(NotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }
    
    public EmailNotification createEmailNotification(
            HtmlEmailTemplate htmlEmailTemplate, Character brand, String email) {
        
        String content = htmlEmailTemplate.getHtmlMessage();
        String sender = htmlEmailTemplate.getSender() == null
                ? EmailBrandSenderEnum.getEmailAddressFromBrand(brand)
                : htmlEmailTemplate.getSender();
        String subject = htmlEmailTemplate.getSubject();
        return EmailNotification.builder()
                .recipient(email)
                .sender(sender)
                .subject(subject)
                .content(content)
                .build();
    }
    
    public void sendEmailNotification(EmailNotification emailNotification) {
        notificationsService
                .sendEmail()
                .invoke(emailNotification)
                .exceptionally(throwable -> {
                    LOGGER.error(throwable.getMessage());
                    throw new MiddlewareTransportException(TransportErrorCode.InternalServerError, throwable);
                });
    }
}
