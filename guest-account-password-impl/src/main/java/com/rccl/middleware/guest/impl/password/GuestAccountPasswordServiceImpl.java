package com.rccl.middleware.guest.impl.password;

import akka.NotUsed;
import akka.japi.Pair;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.server.HeaderServiceCall;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;
import com.rccl.middleware.common.hateoas.HATEOASLinks;
import com.rccl.middleware.common.hateoas.Link;
import com.rccl.middleware.guest.password.EmailNotification;
import com.rccl.middleware.guest.password.GuestAccountPasswordService;
import com.rccl.middleware.guest.password.PasswordInformation;
import com.rccl.middleware.guest.password.exceptions.GuestNotFoundException;
import com.rccl.middleware.guest.password.exceptions.InvalidEmailFormatException;
import com.rccl.middleware.guest.password.exceptions.InvalidGuestPasswordException;
import com.rccl.middleware.saviynt.api.SaviyntGuest;
import com.rccl.middleware.saviynt.api.SaviyntService;
import com.rccl.middleware.saviynt.api.exceptions.SaviyntExceptionFactory;
import org.apache.commons.lang3.StringUtils;
import play.Configuration;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class GuestAccountPasswordServiceImpl implements GuestAccountPasswordService {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private static final String PASSWORD_SERVICE_LINKS = "account-password";
    
    private final GuestAccountPasswordValidator guestAccountPasswordValidator;
    
    private final List<Link> passwordServiceLinks;
    
    private final SaviyntService saviyntService;
    
    private final PersistentEntityRegistry persistentEntityRegistry;
    
    @Inject
    public GuestAccountPasswordServiceImpl(SaviyntService saviyntService,
                                           GuestAccountPasswordValidator guestAccountPasswordValidator,
                                           Configuration configuration,
                                           PersistentEntityRegistry persistentEntityRegistry) {
        this.saviyntService = saviyntService;
        this.guestAccountPasswordValidator = guestAccountPasswordValidator;
        
        this.persistentEntityRegistry = persistentEntityRegistry;
        persistentEntityRegistry.register(EmailNotificationEntity.class);
        
        HATEOASLinks hateoasLinks = new HATEOASLinks(configuration);
        this.passwordServiceLinks = hateoasLinks.getLinks(PASSWORD_SERVICE_LINKS);
    }
    
    @Override
    public HeaderServiceCall<NotUsed, NotUsed> forgotPassword(String email) {
        return (requestHeader, request) -> {
            
            if (!guestAccountPasswordValidator.isValidEmailFormat(email)) {
                throw new InvalidEmailFormatException();
            }
            
            // Invoke Saviynt getUser to get the guest information then combine it
            // with Savyint getResetPasswordLink invocation.
            CompletionStage<JsonNode> getGuestAccountFuture = saviyntService.getGuestAccount("email", email)
                    .invoke()
                    .exceptionally(throwable -> {
                        Throwable cause = throwable.getCause();
                        if (cause instanceof SaviyntExceptionFactory.ExistingGuestException ||
                                cause instanceof SaviyntExceptionFactory.NoSuchGuestException) {
                            throw new GuestNotFoundException();
                        }
                        
                        if (cause instanceof SaviyntExceptionFactory.InvalidEmailFormatException) {
                            throw InvalidGuestPasswordException.INVALID_EMAIL;
                        }
                        
                        throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                    });
            
            return saviyntService.getResetPasswordLink(email)
                    .invoke()
                    .exceptionally(throwable -> {
                        Throwable cause = throwable.getCause();
                        if (cause instanceof SaviyntExceptionFactory.ExistingGuestException ||
                                cause instanceof SaviyntExceptionFactory.NoSuchGuestException) {
                            throw new GuestNotFoundException();
                        }
                        
                        if (cause instanceof SaviyntExceptionFactory.InvalidEmailFormatException) {
                            throw InvalidGuestPasswordException.INVALID_EMAIL;
                        }
                        
                        throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                    })
                    .thenCombineAsync(getGuestAccountFuture, (resetPasswordLinkJsonNode, getGuestAccountJsonNode) -> {
                        String resetPasswordURL = resetPasswordLinkJsonNode.get("url").asText();
                        String guestName = getGuestAccountJsonNode.get("Attributes").get("customproperty1").asText();
                        
                        persistentEntityRegistry.refFor(EmailNotificationEntity.class, email)
                                .ask(new EmailNotificationCommand.SendEmailNotification(
                                        EmailNotification.builder()
                                                .recipient(email)
                                                .sender("notifications@rccl.com")
                                                .subject("Reset your My Cruises password")
                                                .content(this.composeDummyEmailContent(guestName, email, resetPasswordURL))
                                                .build()
                                ));
                        
                        return new Pair<>(ResponseHeader.OK.withStatus(200), NotUsed.getInstance());
                    });
        };
    }
    
    @Override
    public HeaderServiceCall<PasswordInformation, JsonNode> updatePassword(String email) {
        return (requestHeader, request) -> {
            guestAccountPasswordValidator.validateAccountPasswordFields(request, email);
            
            final SaviyntGuest savinyntGuest = this.mapAttributesToSaviynt(request, email);
            return saviyntService
                    .putGuestAccount()
                    .invoke(savinyntGuest)
                    .exceptionally(exception -> {
                        Throwable cause = exception.getCause();
                        
                        if (cause instanceof SaviyntExceptionFactory.ExistingGuestException) {
                            throw new GuestNotFoundException();
                        }
                        
                        if (cause instanceof SaviyntExceptionFactory.InvalidEmailFormatException) {
                            throw InvalidGuestPasswordException.INVALID_EMAIL;
                        }
                        
                        if (cause instanceof SaviyntExceptionFactory.InvalidPasswordFormatException) {
                            throw InvalidGuestPasswordException.INVALID_PASSWORD;
                        }
                        
                        throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), exception);
                    })
                    .thenApply(response -> {
                        ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
                        this.passwordServiceLinks.forEach(link -> link.substituteArguments(email));
                        objectNode.putPOJO("_links", this.passwordServiceLinks);
                        
                        return new Pair<>(ResponseHeader.OK.withStatus(200), objectNode);
                    });
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
    
    /**
     * Sets all the necessary attribute values for password update in Saviynt model.
     *
     * @param passwordInformation {@link PasswordInformation}
     * @param email               {@code String}
     * @return {@code SaviyntGuest}
     */
    private SaviyntGuest mapAttributesToSaviynt(PasswordInformation passwordInformation, String email) {
        return SaviyntGuest.builder()
                .email(email)
                .password(passwordInformation.getPassword())
                .build();
    }
    
    /**
     * Composes a hard-coded email body to temporarily fill up the email JSON message.
     *
     * @param name  Saviynt account's first name
     * @param email Saviynt account's email address
     * @return {@code String}
     */
    private String composeDummyEmailContent(String name, String email, String resetLinkURL) {
        String content = "Hi {0}, \n\n"
                + "We have received a request to reset your My Cruises password.\n\n"
                + "Your username: {1}\n\n"
                + "Click the link below within the next 24 hours to reset your password:\n\n"
                + "{2}\n\n"
                + "After following the link, you will be asked to create a new password.\n\n"
                + "If you didnʼt make this request, please ignore this email.\n\n\n";
        
        return StringUtils.replaceEach(content, new String[]{"{0}", "{1}", "{2}"}, new String[]{name, email, resetLinkURL});
    }
}
