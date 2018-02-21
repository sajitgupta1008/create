package com.rccl.middleware.guest.impl.password.email;

import ch.qos.logback.classic.Logger;
import com.lightbend.lagom.javadsl.api.transport.RequestHeader;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.rccl.middleware.aem.api.email.AemEmailService;
import com.rccl.middleware.aem.api.models.HtmlEmailTemplate;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;
import com.rccl.middleware.common.logging.RcclLoggerFactory;
import com.rccl.middleware.guest.password.PasswordInformation;
import com.rccl.middleware.guest.password.email.EmailNotification;
import com.rccl.middleware.guest.password.exceptions.GuestNotFoundException;
import com.rccl.middleware.saviynt.api.SaviyntService;
import com.rccl.middleware.saviynt.api.exceptions.SaviyntExceptionFactory;
import com.rccl.middleware.saviynt.api.responses.AccountInformation;

import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class PasswordUpdatedConfirmationEmail {
    
    private static final Logger LOGGER = RcclLoggerFactory.getLogger(PasswordUpdatedConfirmationEmail.class);
    
    private AemEmailService aemEmailService;
    
    private PersistentEntityRegistry persistentEntityRegistry;
    
    private SaviyntService saviyntService;
    
    @Inject
    public PasswordUpdatedConfirmationEmail(AemEmailService aemEmailService,
                                            PersistentEntityRegistry persistentEntityRegistry,
                                            SaviyntService saviyntService) {
        this.aemEmailService = aemEmailService;
        this.persistentEntityRegistry = persistentEntityRegistry;
        this.saviyntService = saviyntService;
    }
    
    public void send(PasswordInformation pi, String languageCode) {
        LOGGER.info("#send - Attempting to send the email to: " + pi.getEmail());
        
        this.getGuestInformation(pi)
                .thenAccept(accountInformation -> this.getEmailContent(pi, accountInformation.getGuest().getFirstName(), languageCode)
                        .thenAccept(htmlEmailTemplate -> {
                            String content = htmlEmailTemplate.getHtmlMessage();
                            String sender = htmlEmailTemplate.getSender();
                            String subject = htmlEmailTemplate.getSubject();
                            
                            EmailNotification en = EmailNotification.builder()
                                    .content(content)
                                    .recipient(pi.getEmail())
                                    .sender(sender)
                                    .subject(subject)
                                    .build();
                            
                            this.sendToTopic(en);
                        }));
    }
    
    private CompletionStage<AccountInformation> getGuestInformation(PasswordInformation pi) {
        return saviyntService.getGuestAccount("email", Optional.of(pi.getEmail()), Optional.empty())
                .invoke()
                .exceptionally(throwable -> {
                    Throwable cause = throwable.getCause();
                    
                    if (cause instanceof SaviyntExceptionFactory.ExistingGuestException
                            || cause instanceof SaviyntExceptionFactory.NoSuchGuestException) {
                        throw new GuestNotFoundException();
                    }
                    
                    throw new MiddlewareTransportException(TransportErrorCode.BadRequest, throwable);
                });
    }
    
    private CompletionStage<HtmlEmailTemplate> getEmailContent(PasswordInformation pi, String firstName, String languageCode) {
        if (pi.getHeader() == null) {
            throw new IllegalArgumentException("The header property in the PasswordInformation must not be null.");
        }
        
        Character brand = pi.getHeader().getBrand();
        
        if (brand == null) {
            throw new IllegalArgumentException("The brand header property in the "
                    + "PasswordInformation must not be null.");
        }
        
        Function<Throwable, ? extends HtmlEmailTemplate> exceptionally = throwable -> {
            LOGGER.error("#getEmailContent:", throwable);
            throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
        };
        
        Function<RequestHeader, RequestHeader> acceptLanguageHeader = rh ->
                rh.withHeader("Accept-Language", languageCode);
        
        if ('C' == brand || 'c' == brand) {
            return aemEmailService.getCelebrityPasswordUpdatedConfirmationEmailContent(firstName)
                    .handleRequestHeader(acceptLanguageHeader)
                    .invoke()
                    .exceptionally(exceptionally);
        } else if ('R' == brand || 'r' == brand) {
            return aemEmailService.getRoyalPasswordUpdatedConfirmationEmailContent(firstName)
                    .handleRequestHeader(acceptLanguageHeader)
                    .invoke()
                    .exceptionally(exceptionally);
        }
        
        throw new IllegalArgumentException("An invalid brand value was encountered: " + brand);
    }
    
    private void sendToTopic(EmailNotification emailNotification) {
        persistentEntityRegistry
                .refFor(EmailNotificationEntity.class, emailNotification.getRecipient())
                .ask(new EmailNotificationCommand.SendEmailNotification(emailNotification));
    }
}
