package com.rccl.middleware.guest.impl.password.email;

import ch.qos.logback.classic.Logger;
import com.lightbend.lagom.javadsl.api.transport.RequestHeader;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.rccl.middleware.aem.api.email.AemEmailService;
import com.rccl.middleware.aem.api.models.HtmlEmailTemplate;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;
import com.rccl.middleware.common.logging.RcclLoggerFactory;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class AemEmailHelper {
    
    private static final Logger LOGGER = RcclLoggerFactory.getLogger(AemEmailHelper.class);
    private final AemEmailService aemEmailService;
    
    @Inject
    public AemEmailHelper(AemEmailService aemEmailService) {
        this.aemEmailService = aemEmailService;
    }
    
    public CompletionStage<HtmlEmailTemplate> getEmailContent(Character brand, String firstName,
                                                              RequestHeader aemEmailRequestHeader, String resetPasswordUrl) {
        
        Function<Throwable, ? extends HtmlEmailTemplate> exceptionally = throwable -> {
            LOGGER.error("#getEmailContent:", throwable);
            throw new MiddlewareTransportException(TransportErrorCode.InternalServerError, throwable);
        };
        
        String acceptLanguage = aemEmailRequestHeader.getHeader("Accept-Language").orElse("");
        Function<RequestHeader, RequestHeader> aemEmailServiceHeader = rh -> rh
                .withHeader("Accept-Language", acceptLanguage);
        
        if (resetPasswordUrl == null) {
            return getPasswordUpdatedConfirmationEmailContent(brand, firstName, aemEmailServiceHeader, exceptionally);
        } else {
            return getForgotPasswordEmailContent(brand, firstName, aemEmailServiceHeader, exceptionally, resetPasswordUrl);
        }
        
    }
    
    private CompletionStage<HtmlEmailTemplate> getPasswordUpdatedConfirmationEmailContent(Character brand,
                                                                                          String firstName,
                                                                                          Function<RequestHeader, RequestHeader> aemEmailServiceHeader,
                                                                                          Function<Throwable, ? extends HtmlEmailTemplate> exceptionally) {
        if ('C' == brand || 'c' == brand) {
            return aemEmailService.getCelebrityPasswordUpdatedConfirmationEmailContent(firstName)
                    .handleRequestHeader(aemEmailServiceHeader)
                    .invoke()
                    .exceptionally(exceptionally);
        } else if ('R' == brand || 'r' == brand) {
            return aemEmailService.getRoyalPasswordUpdatedConfirmationEmailContent(firstName)
                    .handleRequestHeader(aemEmailServiceHeader)
                    .invoke()
                    .exceptionally(exceptionally);
        }
        
        throw new IllegalArgumentException("An invalid brand value was encountered: " + brand);
        
    }
    
    private CompletionStage<HtmlEmailTemplate> getForgotPasswordEmailContent(Character brand,
                                                                             String firstName,
                                                                             Function<RequestHeader, RequestHeader> aemEmailServiceHeader,
                                                                             Function<Throwable, ? extends HtmlEmailTemplate> exceptionally,
                                                                             String resetPasswordUrl) {
        if ('C' == brand || 'c' == brand) {
            return aemEmailService.getCelebrityForgotPasswordEmailContent(firstName, resetPasswordUrl)
                    .handleRequestHeader(aemEmailServiceHeader)
                    .invoke()
                    .exceptionally(exceptionally);
        } else if ('R' == brand || 'r' == brand) {
            return aemEmailService.getRoyalForgotPasswordEmailContent(firstName, resetPasswordUrl)
                    .handleRequestHeader(aemEmailServiceHeader)
                    .invoke()
                    .exceptionally(exceptionally);
        }
        
        throw new IllegalArgumentException("An invalid brand value was encountered: " + brand);
    }
    
    
}
