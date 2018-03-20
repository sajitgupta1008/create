package com.rccl.middleware.guest.impl.password.email;

import ch.qos.logback.classic.Logger;
import com.lightbend.lagom.javadsl.api.transport.RequestHeader;
import com.rccl.middleware.common.logging.RcclLoggerFactory;
import com.rccl.middleware.guest.password.ForgotPassword;
import com.rccl.middleware.notifications.EmailNotification;

import javax.inject.Inject;

public class ResetPasswordEmail {
    
    private static final Logger LOGGER = RcclLoggerFactory.getLogger(ResetPasswordEmail.class);
    
    private AemEmailHelper aemEmailHelper;
    
    private NotificationsHelper notificationsHelper;
    
    @Inject
    public ResetPasswordEmail(AemEmailHelper aemEmailHelper,
                              NotificationsHelper notificationsHelper) {
        this.aemEmailHelper = aemEmailHelper;
        this.notificationsHelper = notificationsHelper;
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
        
        if (fp.getHeader() == null) {
            throw new IllegalArgumentException("The header property in the ForgotPassword must not be null.");
        }
        
        Character brand = fp.getHeader().getBrand();
        
        if (brand == null) {
            throw new IllegalArgumentException("The brand header property in the ForgotPassword must not be null.");
        }
        
        aemEmailHelper.getEmailContent(brand, firstName, requestHeader, resetPasswordUrl)
                .thenAccept(htmlEmailTemplate -> {
                    if (htmlEmailTemplate != null) {
                        EmailNotification emailNotification = notificationsHelper.createEmailNotification(
                                htmlEmailTemplate, brand, email);
                        notificationsHelper.sendEmailNotification(emailNotification);
                    }
                });
    }
}
