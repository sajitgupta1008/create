package com.rccl.middleware.guest.impl.password;

import akka.NotUsed;
import akka.japi.Pair;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.server.HeaderServiceCall;
import com.rccl.middleware.aem.api.AemService;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;
import com.rccl.middleware.common.validation.MiddlewareValidation;
import com.rccl.middleware.forgerock.api.ForgeRockCredentials;
import com.rccl.middleware.forgerock.api.ForgeRockService;
import com.rccl.middleware.forgerock.api.LoginStatusEnum;
import com.rccl.middleware.forgerock.api.exceptions.ForgeRockExceptionFactory;
import com.rccl.middleware.guest.password.EmailNotification;
import com.rccl.middleware.guest.password.ForgotPassword;
import com.rccl.middleware.guest.password.ForgotPasswordToken;
import com.rccl.middleware.guest.password.GuestAccountPasswordService;
import com.rccl.middleware.guest.password.PasswordInformation;
import com.rccl.middleware.guest.password.exceptions.GuestAuthenticationException;
import com.rccl.middleware.guest.password.exceptions.GuestNotFoundException;
import com.rccl.middleware.guest.password.exceptions.InvalidEmailException;
import com.rccl.middleware.guest.password.exceptions.InvalidPasswordException;
import com.rccl.middleware.guest.password.exceptions.InvalidPasswordTokenException;
import com.rccl.middleware.saviynt.api.SaviyntService;
import com.rccl.middleware.saviynt.api.SaviyntUpdatePassword;
import com.rccl.middleware.saviynt.api.SaviyntUserToken;
import com.rccl.middleware.saviynt.api.exceptions.SaviyntExceptionFactory;
import com.rccl.ops.common.logging.RcclLoggerFactory;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class GuestAccountPasswordServiceImpl implements GuestAccountPasswordService {
    
    private static final Logger LOGGER = RcclLoggerFactory.getLogger(GuestAccountPasswordServiceImpl.class);
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private final GuestAccountPasswordValidator guestAccountPasswordValidator;
    
    private final AemService aemService;
    
    private final ForgeRockService forgeRockService;
    
    private final SaviyntService saviyntService;
    
    private final PersistentEntityRegistry persistentEntityRegistry;
    
    @Inject
    public GuestAccountPasswordServiceImpl(AemService aemService,
                                           ForgeRockService forgeRockService,
                                           SaviyntService saviyntService,
                                           GuestAccountPasswordValidator guestAccountPasswordValidator,
                                           PersistentEntityRegistry persistentEntityRegistry) {
        this.aemService = aemService;
        this.forgeRockService = forgeRockService;
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
            
            return saviyntService.getForgotPasswordTokenAndStatus(email, "email").invoke()
                    .exceptionally(throwable -> {
                        throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                    })
                    .thenCompose(forgotPasswordJson -> {
                        CompletionStage<JsonNode> aemEmailTemplateFuture = getAEMTemplateFuture(forgotPasswordJson);
                        
                        // Invoke Saviynt getUser to get the guest information then combine it with AEM Email
                        // template call.
                        JsonNode vdsId = forgotPasswordJson.get("VDSID");
                        JsonNode userToken = forgotPasswordJson.get("TOKEN");
                        return saviyntService
                                .getGuestAccount("email", Optional.of(email), Optional.empty())
                                .invoke()
                                .exceptionally(throwable -> {
                                    LOGGER.error("Error occurred while retrieving guest account details for email : " + email);
                                    Throwable cause = throwable.getCause();
                                    if (cause instanceof SaviyntExceptionFactory.ExistingGuestException
                                            || cause instanceof SaviyntExceptionFactory.NoSuchGuestException) {
                                        throw new GuestNotFoundException();
                                    } else if (cause instanceof SaviyntExceptionFactory.InvalidEmailFormatException) {
                                        throw new InvalidEmailException();
                                    }
                                    
                                    throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                                    
                                }).thenCombineAsync(aemEmailTemplateFuture, (saviyntResponse, aemResponse) -> {
                                    ObjectNode combinedResponse = saviyntResponse.deepCopy();
                                    combinedResponse.set("emailResponse", aemResponse);
                                    
                                    StringBuilder resetPasswordURL = new StringBuilder(request.getLink());
                                    
                                    // pass VDS ID and user token parameters for reset password if it's returned
                                    // from forgotPasswordAccountStatus response.
                                    if (vdsId != null && userToken != null) {
                                        resetPasswordURL.append("?vdsId=" + vdsId.asText())
                                                .append("&username=" + email)
                                                .append("&token=" + userToken.asText());
                                    }
                                    
                                    LOGGER.info("Sending email notification to reset password");
                                    
                                    persistentEntityRegistry.refFor(EmailNotificationEntity.class, email)
                                            .ask(new EmailNotificationCommand.SendEmailNotification(
                                                    EmailNotification.builder()
                                                            .recipient(email)
                                                            .sender("notifications@rccl.com")
                                                            .subject("Reset your My Cruises password")
                                                            .content(this.composeEmailContent(combinedResponse, resetPasswordURL.toString()))
                                                            .build()
                                            ));
                                    
                                    return Pair.create(ResponseHeader.OK.withStatus(200), NotUsed.getInstance());
                                });
                    });
        };
    }
    
    @Override
    public HeaderServiceCall<ForgotPasswordToken, NotUsed> validateForgotPasswordToken() {
        return (requestHeader, request) -> {
            LOGGER.info("Processing forgot password token validation...");
            
            SaviyntUserToken saviyntUserToken;
            
            // populate user with email address|VDS ID and token if VDS ID is specified in the request,
            // otherwise, do the WebShopper approach with shopperId|shopperUserName|firstName|lastName
            if (StringUtils.isNotBlank(request.getVdsId())) {
                MiddlewareValidation.validateWithGroups(request, ForgotPasswordToken.NewUserChecks.class);
                
                saviyntUserToken = SaviyntUserToken.builder()
                        .user(request.getEmail() + "|" + request.getVdsId())
                        .token(request.getToken())
                        .build();
                
            } else {
                MiddlewareValidation.validateWithGroups(request, ForgotPasswordToken.WebShopperChecks.class);
                saviyntUserToken = SaviyntUserToken.builder()
                        .user(request.getWebShopperId() + "|"
                                + request.getWebShopperUserName() + "|"
                                + request.getFirstName() + "|"
                                + request.getLastName())
                        .token(request.getToken())
                        .build();
            }
            
            return saviyntService.validateUserToken().invoke(saviyntUserToken)
                    .exceptionally(throwable -> {
                        Throwable cause = throwable.getCause();
                        
                        if (cause instanceof SaviyntExceptionFactory.InvalidUserTokenException) {
                            throw new InvalidPasswordTokenException();
                        } else if (cause instanceof SaviyntExceptionFactory.NoSuchGuestException) {
                            throw new GuestNotFoundException();
                        }
                        
                        throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                    })
                    .thenApply(notUsed ->
                            Pair.create(ResponseHeader.OK, NotUsed.getInstance())
                    );
        };
    }
    
    @Override
    public HeaderServiceCall<PasswordInformation, JsonNode> updatePassword() {
        return (requestHeader, request) -> {
            
            LOGGER.info("processing update-password request");
            
            guestAccountPasswordValidator.validateAccountPasswordFields(request);
            
            final SaviyntUpdatePassword savinyntPassword = this.mapAttributesToSaviynt(request);
            
            if (StringUtils.isNotBlank(savinyntPassword.getToken())) {
                return saviyntService.updateAccountPasswordWithToken().invoke(savinyntPassword)
                        .exceptionally(throwable -> {
                            Throwable cause = throwable.getCause();
                            if (cause instanceof SaviyntExceptionFactory.ExistingGuestException) {
                                throw new GuestNotFoundException();
                            } else if (cause instanceof SaviyntExceptionFactory.InvalidEmailFormatException) {
                                throw new InvalidEmailException();
                            } else if (cause instanceof SaviyntExceptionFactory.InvalidPasswordFormatException) {
                                throw new InvalidPasswordException();
                            }
                            
                            throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                        })
                        .thenCompose(response -> {
                            //TODO fix once Saviynt error response is fixed.
                            if (response.get("errorMessage") != null) {
                                throw new InvalidPasswordTokenException();
                            }
                            
                            return this.authenticateUser(request);
                        });
            } else {
                return saviyntService.updateAccountPasswordWithQuestionAndAnswer().invoke(savinyntPassword)
                        .exceptionally(throwable -> {
                            Throwable cause = throwable.getCause();
                            if (cause instanceof SaviyntExceptionFactory.ExistingGuestException) {
                                throw new GuestNotFoundException();
                            } else if (cause instanceof SaviyntExceptionFactory.InvalidEmailFormatException) {
                                throw new InvalidEmailException();
                            } else if (cause instanceof SaviyntExceptionFactory.InvalidPasswordFormatException) {
                                throw new InvalidPasswordException();
                            }
                            
                            throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                        })
                        .thenCompose(response -> {
                            //TODO fix once Saviynt error response is fixed.
                            JsonNode messageJson = response.get("Message :");
                            if (messageJson != null && !messageJson.asText().contains("Change Password Successful")) {
                                throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500),
                                        messageJson.asText());
                            }
                            
                            return this.authenticateUser(request);
                        });
            }
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
     * Authenticates user ONLY if channel is either {@code app-ios} or {@code app-android}.
     * If the channel specified in the header is {@code web}, return immediately.
     *
     * @param passwordInformation the {@link PasswordInformation} request.
     * @return {@link CompletionStage}
     */
    private CompletionStage<Pair<ResponseHeader, JsonNode>> authenticateUser(PasswordInformation passwordInformation) {
        if (passwordInformation.getHeader() != null
                && "web".equals(passwordInformation.getHeader().getChannel())) {
            return CompletableFuture.completedFuture(Pair.create(ResponseHeader.OK, OBJECT_MAPPER.createObjectNode()));
        }
        ForgeRockCredentials forgeRockCredentials = ForgeRockCredentials.builder()
                .password(passwordInformation.getPassword())
                .username(passwordInformation.getEmail())
                .build();
        
        return forgeRockService.authenticateMobileUser().invoke(forgeRockCredentials)
                .exceptionally(throwable -> {
                    Throwable cause = throwable.getCause();
                    
                    if (cause instanceof ForgeRockExceptionFactory.AuthenticationException) {
                        ForgeRockExceptionFactory.AuthenticationException ex =
                                (ForgeRockExceptionFactory.AuthenticationException) cause;
                        throw new GuestAuthenticationException(ex.getErrorDescription());
                    }
                    
                    throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                })
                .thenApply(jsonNode -> {
                    ObjectNode jsonResponse = OBJECT_MAPPER.createObjectNode();
                    jsonResponse.put("accountLoginStatus", LoginStatusEnum.NEW_ACCOUNT_AUTHENTICATED.value());
                    jsonResponse.put("accessToken", jsonNode.get("access_token").asText());
                    jsonResponse.put("refreshToken", jsonNode.get("refresh_token").asText());
                    jsonResponse.put("openIdToken", jsonNode.get("id_token").asText());
                    jsonResponse.put("tokenExpiration", jsonNode.get("expires_in").asText());
                    
                    return Pair.create(ResponseHeader.OK, jsonResponse);
                });
    }
    
    /**
     * Returns a {@link CompletionStage} of an AEM email template service which depends on the response status of a user.
     *
     * @param responseJson JSON response of forgotPasswordAccountStatus Saviynt service.
     * @return {@link CompletionStage}
     */
    private CompletionStage<JsonNode> getAEMTemplateFuture(JsonNode responseJson) {
        CompletionStage<JsonNode> aemEmailTemplateFuture = null;
        
        JsonNode message = responseJson.get("message");
        if (message != null) {
            if ("NeedsToBeMigrated".equals(message.asText())) {
                aemEmailTemplateFuture = aemService.getResetPasswordEmailMigration(GuestAccountPasswordService.SHIP_CODE)
                        .invoke()
                        .exceptionally(throwable -> {
                            LOGGER.error("Error occurred while retrieving AEM reset password email template.");
                            throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                        });
            } else if ("DoesNotExist".equals(message.asText())) {
                throw new GuestNotFoundException();
            }
            
        } else {
            aemEmailTemplateFuture = aemService.getResetPasswordEmailMigration(GuestAccountPasswordService.SHIP_CODE)
                    .invoke()
                    .exceptionally(throwable -> {
                        LOGGER.error("Error occurred while retrieving AEM migration email template.");
                        throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                    });
        }
        
        return aemEmailTemplateFuture;
    }
    
    /**
     * Sets all the necessary attribute values for password update in Saviynt model.
     *
     * @param passwordInformation {@link PasswordInformation}
     * @return {@code SaviyntGuest}
     */
    private SaviyntUpdatePassword mapAttributesToSaviynt(PasswordInformation passwordInformation) {
        return SaviyntUpdatePassword.builder()
                .vdsId(passwordInformation.getVdsId())
                .email(passwordInformation.getEmail())
                .securityQuestion(passwordInformation.getSecurityQuestion())
                .securityAnswer(passwordInformation.getSecurityAnswer())
                .password(Arrays.toString(passwordInformation.getPassword()))
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
