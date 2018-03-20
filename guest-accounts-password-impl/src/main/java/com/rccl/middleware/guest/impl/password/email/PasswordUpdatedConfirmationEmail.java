package com.rccl.middleware.guest.impl.password.email;

import ch.qos.logback.classic.Logger;
import com.lightbend.lagom.javadsl.api.transport.RequestHeader;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;
import com.rccl.middleware.common.logging.RcclLoggerFactory;
import com.rccl.middleware.guest.password.PasswordInformation;
import com.rccl.middleware.guest.password.exceptions.GuestNotFoundException;
import com.rccl.middleware.notifications.EmailNotification;
import com.rccl.middleware.saviynt.api.SaviyntService;
import com.rccl.middleware.saviynt.api.exceptions.SaviyntExceptionFactory;
import com.rccl.middleware.saviynt.api.responses.AccountInformation;

import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public class PasswordUpdatedConfirmationEmail {
    
    private static final Logger LOGGER = RcclLoggerFactory.getLogger(PasswordUpdatedConfirmationEmail.class);
    
    private AemEmailHelper aemEmailHelper;
    
    private SaviyntService saviyntService;
    
    private NotificationsHelper notificationsHelper;
    
    @Inject
    public PasswordUpdatedConfirmationEmail(AemEmailHelper aemEmailHelper,
                                            SaviyntService saviyntService,
                                            NotificationsHelper notificationsHelper) {
        this.notificationsHelper = notificationsHelper;
        this.saviyntService = saviyntService;
        this.aemEmailHelper = aemEmailHelper;
    }
    
    /**
     * Retrieves all the necessary information from both VDS and AEM for password update email confirmation.
     *
     * @param pi            the {@link PasswordInformation} from service request invocation.
     * @param requestHeader the {@link RequestHeader} from service request invocation.
     */
    public void send(PasswordInformation pi, RequestHeader requestHeader) {
        LOGGER.info("#send - Attempting to send the email to: " + pi.getEmail());
        
        if (pi.getHeader() == null) {
            throw new IllegalArgumentException("The header property in the PasswordInformation must not be null.");
        }
        
        Character brand = pi.getHeader().getBrand();
        
        this.getGuestInformation(pi)
                .thenAccept(accountInformation -> {
                    if (brand == null) {
                        throw new IllegalArgumentException("The brand header property in the "
                                + "PasswordInformation must not be null.");
                    }
                    
                    aemEmailHelper.getEmailContent(brand, accountInformation.getGuest()
                            .getFirstName(), requestHeader, null)
                            .thenAccept(htmlEmailTemplate -> {
                                if (htmlEmailTemplate != null) {
                                    EmailNotification emailNotification = notificationsHelper.createEmailNotification(
                                            htmlEmailTemplate, brand, pi.getEmail());
                                    notificationsHelper.sendEmailNotification(emailNotification);
                                }
                            });
                });
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
}
