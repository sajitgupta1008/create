package com.rccl.middleware.guest.impl.password.email;

import ch.qos.logback.classic.Logger;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.rccl.middleware.aem.api.email.AemEmailService;
import com.rccl.middleware.aem.api.models.HtmlEmailTemplate;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;
import com.rccl.middleware.common.logging.RcclLoggerFactory;
import com.rccl.middleware.guest.password.email.EmailNotification;
import com.rccl.middleware.guest.password.ForgotPassword;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class ResetPasswordEmail {
    
    private static final Logger LOGGER = RcclLoggerFactory.getLogger(ResetPasswordEmail.class);
    
    private AemEmailService aemEmailService;
    
    private PersistentEntityRegistry persistentEntityRegistry;
    
    @Inject
    public ResetPasswordEmail(AemEmailService aemEmailService,
                              PersistentEntityRegistry persistentEntityRegistry) {
        this.aemEmailService = aemEmailService;
        this.persistentEntityRegistry = persistentEntityRegistry;
    }
    
    public void send(ForgotPassword fp, String email, String firstName, String resetPasswordUrl) {
        this.getEmailContent(fp, firstName, resetPasswordUrl)
                .thenAccept(htmlEmailTemplate -> {
                    String content = htmlEmailTemplate.getHtmlMessage();
                    String sender = htmlEmailTemplate.getSender();
                    String subject = htmlEmailTemplate.getSubject();
                    
                    EmailNotification en = EmailNotification.builder()
                            .content(content)
                            .recipient(email)
                            .sender(sender)
                            .subject(subject)
                            .build();
                    
                    this.sendToTopic(en);
                });
    }
    
    private CompletionStage<HtmlEmailTemplate> getEmailContent(ForgotPassword fp, String firstName, String resetPasswordUrl) {
        if (fp.getHeader() == null) {
            throw new IllegalArgumentException("The header property in the ForgotPassword must not be null.");
        }
        
        Character brand = fp.getHeader().getBrand();
        
        if (brand == null) {
            throw new IllegalArgumentException("The brand header property in the ForgotPassword must not be null.");
        }
        
        Function<Throwable, ? extends HtmlEmailTemplate> exceptionally = throwable -> {
            LOGGER.error("#getEmailContent:", throwable);
            throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
        };
        
        if ('C' == brand || 'c' == brand) {
            return aemEmailService.getCelebrityForgotPasswordEmailContent(firstName, resetPasswordUrl)
                    .invoke()
                    .exceptionally(exceptionally);
        } else if ('R' == brand || 'r' == brand) {
            return aemEmailService.getRoyalForgotPasswordEmailContent(firstName, resetPasswordUrl)
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
