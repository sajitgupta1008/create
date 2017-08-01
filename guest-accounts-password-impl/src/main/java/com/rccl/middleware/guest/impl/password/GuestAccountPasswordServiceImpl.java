package com.rccl.middleware.guest.impl.password;

import akka.NotUsed;
import akka.japi.Pair;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.server.HeaderServiceCall;
import com.rccl.middleware.aem.api.AemService;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;
import com.rccl.middleware.guest.password.EmailNotification;
import com.rccl.middleware.guest.password.ForgotPassword;
import com.rccl.middleware.guest.password.GuestAccountPasswordService;
import com.rccl.middleware.guest.password.PasswordInformation;
import com.rccl.middleware.guest.password.exceptions.GuestNotFoundException;
import com.rccl.middleware.guest.password.exceptions.InvalidEmailException;
import com.rccl.middleware.guest.password.exceptions.InvalidPasswordException;
import com.rccl.middleware.saviynt.api.SaviyntGuest;
import com.rccl.middleware.saviynt.api.SaviyntService;
import com.rccl.middleware.saviynt.api.SaviyntUserToken;
import com.rccl.middleware.saviynt.api.exceptions.SaviyntExceptionFactory;
import com.rccl.ops.common.logging.RcclLoggerFactory;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class GuestAccountPasswordServiceImpl implements GuestAccountPasswordService {
    
    private static final Logger LOGGER = RcclLoggerFactory.getLogger(GuestAccountPasswordServiceImpl.class);
    
    private final GuestAccountPasswordValidator guestAccountPasswordValidator;
    
    private final AemService aemService;
    
    private final SaviyntService saviyntService;
    
    private final PersistentEntityRegistry persistentEntityRegistry;
    
    @Inject
    public GuestAccountPasswordServiceImpl(AemService aemService,
                                           SaviyntService saviyntService,
                                           GuestAccountPasswordValidator guestAccountPasswordValidator,
                                           PersistentEntityRegistry persistentEntityRegistry) {
        this.aemService = aemService;
        this.saviyntService = saviyntService;
        this.guestAccountPasswordValidator = guestAccountPasswordValidator;
        
        this.persistentEntityRegistry = persistentEntityRegistry;
        persistentEntityRegistry.register(EmailNotificationEntity.class);
    }
    
    @Override
    public HeaderServiceCall<ForgotPassword, NotUsed> forgotPassword(String email) {
        return (requestHeader, request) -> {
            
            LOGGER.info("Processing forgot-password request for email : " + email);
            
            guestAccountPasswordValidator.validateForgotPasswordFields(request, email);
            
            SaviyntUserToken saviyntUserToken = SaviyntUserToken.builder().user(email).build();
            
            CompletionStage<JsonNode> aemEmailTemplateFuture = aemService
                    .getResetPasswordEmail(GuestAccountPasswordService.SHIP_CODE).invoke()
                    .exceptionally(throwable -> {
                        LOGGER.error("Error occurred while retrieving AEM email template.");
                        throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                    });
            
            // Invoke Saviynt getUser to get the guest information then combine it with AEM Email template call,
            // then combine with Savyint getResetPasswordLink invocation.
            CompletionStage<JsonNode> getGuestAccountFuture = saviyntService
                    .getGuestAccount("email", Optional.of(email), Optional.empty())
                    .invoke()
                    .exceptionally(throwable -> {
                        LOGGER.error("Error occurred while retrieving guest account details for email : " + email);
                        Throwable cause = throwable.getCause();
                        if (cause instanceof SaviyntExceptionFactory.ExistingGuestException
                                || cause instanceof SaviyntExceptionFactory.NoSuchGuestException) {
                            throw new GuestNotFoundException();
                        }
                        
                        if (cause instanceof SaviyntExceptionFactory.InvalidEmailFormatException) {
                            throw new InvalidEmailException();
                        }
                        
                        throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                    }).thenCombineAsync(aemEmailTemplateFuture, (saviyntResponse, aemResponse) -> {
                        ObjectNode combinedResponse = saviyntResponse.deepCopy();
                        combinedResponse.set("emailResponse", aemResponse);
                        
                        return combinedResponse;
                    });
            
            return saviyntService.getUserToken()
                    .invoke(saviyntUserToken)
                    .exceptionally(throwable -> {
                        Throwable cause = throwable.getCause();
                        if (cause instanceof SaviyntExceptionFactory.MissingUsernameException) {
                            throw new GuestNotFoundException();
                        }
                        
                        throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                    })
                    .thenCombineAsync(getGuestAccountFuture, (userTokenJsonNode, getUserAndAemCombinedResponse) -> {
                        String userToken = userTokenJsonNode.get("TOKEN").asText();
                        
                        StringBuilder resetPasswordURL = new StringBuilder(request.getLink());
                        resetPasswordURL.append("?token=");
                        resetPasswordURL.append(userToken);
                        
                        LOGGER.info("Sending email notification to reset password");
                        
                        persistentEntityRegistry.refFor(EmailNotificationEntity.class, email)
                                .ask(new EmailNotificationCommand.SendEmailNotification(
                                        EmailNotification.builder()
                                                .recipient(email)
                                                .sender("notifications@rccl.com")
                                                .subject("Reset your My Cruises password")
                                                .content(this.composeEmailContent(getUserAndAemCombinedResponse, resetPasswordURL.toString()))
                                                .build()
                                ));
                        
                        return new Pair<>(ResponseHeader.OK.withStatus(200), NotUsed.getInstance());
                    });
        };
    }
    
    @Override
    public HeaderServiceCall<PasswordInformation, NotUsed> updatePassword(String vdsId) {
        return (requestHeader, request) -> {
            
            LOGGER.info("processing update-password request");
            
            guestAccountPasswordValidator.validateAccountPasswordFields(request, vdsId);
            
            final SaviyntGuest savinyntGuest = this.mapAttributesToSaviynt(request, vdsId);
            return saviyntService
                    .updateGuestAccount()
                    .invoke(savinyntGuest)
                    .exceptionally(exception -> {
                        Throwable cause = exception.getCause();
                        
                        if (cause instanceof SaviyntExceptionFactory.ExistingGuestException) {
                            throw new GuestNotFoundException();
                        }
                        
                        if (cause instanceof SaviyntExceptionFactory.InvalidEmailFormatException) {
                            throw new InvalidEmailException();
                        }
                        
                        if (cause instanceof SaviyntExceptionFactory.InvalidPasswordFormatException) {
                            throw new InvalidPasswordException();
                        }
                        
                        throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), exception);
                    })
                    .thenApply(response -> new Pair<>(ResponseHeader.OK.withStatus(200), NotUsed.getInstance()));
        };
    }
    
    @Override
    public Topic<EmailNotification> emailNotificationTopic() {
        return TopicProducer.singleStreamWithOffset(offset ->
                persistentEntityRegistry
                        .eventStream(EmailNotificationTag.EMAIL_NOTIFICATION_TAG, offset)
                        .map(pair -> {
                            EmailNotification eventNotification = pair.first().getEmailNotification();
                            EmailNotification emailNotification = EmailNotification
                                    .builder()
                                    .sender(eventNotification.getSender())
                                    .recipient(eventNotification.getRecipient())
                                    .subject(eventNotification.getSubject())
                                    .content(eventNotification.getContent())
                                    .build();
                            return new Pair<>(emailNotification, pair.second());
                        })
        );
    }
    
    @Override
    public HeaderServiceCall<NotUsed, String> healthCheck() {
        return (requestHeader, request) -> {
            String quote = "Here's to tall ships. "
                    + "Here's to small ships. "
                    + "Here's to all the ships on the sea. "
                    + "But the best ships are friendships, so here's to you and me!";
            
            LOGGER.info("HealthCheck : " + quote);
            return CompletableFuture.completedFuture(new Pair<>(ResponseHeader.OK, quote));
        };
    }
    
    /**
     * Sets all the necessary attribute values for password update in Saviynt model.
     *
     * @param passwordInformation {@link PasswordInformation}
     * @param vdsId               {@code String}
     * @return {@code SaviyntGuest}
     */
    private SaviyntGuest mapAttributesToSaviynt(PasswordInformation passwordInformation, String vdsId) {
        return SaviyntGuest.builder()
                .vdsId(vdsId)
                .password(passwordInformation.getPassword())
                .propertytosearch("systemUserName")
                .build();
    }
    
    /**
     * Replaces the email content variables with the proper guest attributes.
     *
     * @param getUserAndAemCombinedResponse combined Saviynt getGuest and AEM reset password email responses
     * @param resetLinkURL                  service consumer provided URL appended with TOKEN from Saviynt
     * @return {@link String} Email Content
     */
    private String composeEmailContent(JsonNode getUserAndAemCombinedResponse, String resetLinkURL) {
        ObjectNode guestJson = getUserAndAemCombinedResponse.deepCopy();
        String name = guestJson.get("Attributes").get("firstname").asText();
        String email = guestJson.get("Attributes").get("email").asText();
        String emailContent = guestJson.findValue("data").get("text").asText(); // this is coming from "emailResponse"
        
        return StringUtils.replaceEach(
                emailContent,
                new String[]{"<first name>", "<guest username/email>", "<link to reset>"},
                new String[]{name, email, resetLinkURL}
        );
    }
}
