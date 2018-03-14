package com.rccl.middleware.guest.impl.password.email;

import ch.qos.logback.classic.Logger;
import com.lightbend.lagom.javadsl.api.transport.RequestHeader;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.rccl.middleware.aem.api.email.AemEmailService;
import com.rccl.middleware.aem.api.models.HtmlEmailTemplate;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;
import com.rccl.middleware.common.logging.RcclLoggerFactory;
import com.rccl.middleware.guest.password.ForgotPassword;
import com.rccl.middleware.notification.email.EmailNotification;
import com.rccl.middleware.notification.email.EmailNotificationService;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class ResetPasswordEmail {
    
    private static final Logger LOGGER = RcclLoggerFactory.getLogger(ResetPasswordEmail.class);
    
    private AemEmailService aemEmailService;
    
    private EmailNotificationService emailNotificationService;
    
    @Inject
    public ResetPasswordEmail(AemEmailService aemEmailService,
                              EmailNotificationService emailNotificationService) {
        this.aemEmailService = aemEmailService;
        this.emailNotificationService = emailNotificationService;
    }
    
    /**
     * Retrieves all the necessary information from both VDS and AEM for reset password email confirmation.
     *
     * @param fp               the {@link ForgotPassword} object from request service invocation.
     * @param email            the email address where the email will be sent to.
     * @param firstName        the first name of the guest who owns the email address.
     * @param resetPasswordUrl the reset password URL to be included in the email.
     * @param requestHeader    the {@link RequestHeader} from service request invocation.
     */
    public void send(ForgotPassword fp, String email, String firstName, String resetPasswordUrl,
                     RequestHeader requestHeader) {
        LOGGER.info("#send - Attempting to send the email to: {}", email);
        
        if (fp.getHeader() == null) {
            throw new IllegalArgumentException("The header property in the ForgotPassword must not be null.");
        }
        
        this.getEmailContent(fp, firstName, resetPasswordUrl, requestHeader)
                .thenAccept(htmlEmailTemplate -> {
                    String content = htmlEmailTemplate.getHtmlMessage();
                    String sender = htmlEmailTemplate.getSender() == null
                            ? EmailBrandSenderEnum.getEmailAddressFromBrand(fp.getHeader().getBrand())
                            : htmlEmailTemplate.getSender();
                    String subject = htmlEmailTemplate.getSubject();
                    
                    EmailNotification en = EmailNotification.builder()
                            .content(content)
                            .recipient(email)
                            .sender(sender)
                            .subject(subject)
                            .build();
                    
                    this.sendEmailNotification(en);
                });
    }
    
    private CompletionStage<HtmlEmailTemplate> getEmailContent(ForgotPassword fp, String firstName,
                                                               String resetPasswordUrl, RequestHeader requestHeader) {
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
        
        String acceptLanguage = requestHeader.getHeader("Accept-Language").orElse("");
        Function<RequestHeader, RequestHeader> aemRequestHeaderFunction = rh ->
                rh.withHeader("Accept-Language", acceptLanguage);
        
        if ('C' == brand || 'c' == brand) {
            return aemEmailService.getCelebrityForgotPasswordEmailContent(firstName, resetPasswordUrl)
                    .handleRequestHeader(aemRequestHeaderFunction)
                    .invoke()
                    .exceptionally(exceptionally);
        } else if ('R' == brand || 'r' == brand) {
            return aemEmailService.getRoyalForgotPasswordEmailContent(firstName, resetPasswordUrl)
                    .handleRequestHeader(aemRequestHeaderFunction)
                    .invoke()
                    .exceptionally(exceptionally);
        }
        
        throw new IllegalArgumentException("An invalid brand value was encountered: " + brand);
    }
    
    private void sendEmailNotification(EmailNotification emailNotification) {
        emailNotificationService
                .notification()
                .invoke(emailNotification)
                .exceptionally(throwable -> {
                    LOGGER.error(throwable.getMessage());
                    throw new MiddlewareTransportException(TransportErrorCode.InternalServerError, throwable);
                });
    }
}
