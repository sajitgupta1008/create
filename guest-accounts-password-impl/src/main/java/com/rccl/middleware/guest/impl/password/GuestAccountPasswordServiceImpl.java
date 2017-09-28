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
import com.rccl.middleware.common.response.ResponseBody;
import com.rccl.middleware.common.validation.MiddlewareValidation;
import com.rccl.middleware.forgerock.api.ForgeRockService;
import com.rccl.middleware.forgerock.api.exceptions.ForgeRockExceptionFactory;
import com.rccl.middleware.forgerock.api.jwt.ForgeRockJWTDecoder;
import com.rccl.middleware.forgerock.api.jwt.OpenIdTokenInformation;
import com.rccl.middleware.forgerock.api.requests.ForgeRockCredentials;
import com.rccl.middleware.forgerock.api.requests.LoginStatusEnum;
import com.rccl.middleware.guest.password.EmailNotification;
import com.rccl.middleware.guest.password.ForgotPassword;
import com.rccl.middleware.guest.password.ForgotPasswordToken;
import com.rccl.middleware.guest.password.GuestAccountPasswordService;
import com.rccl.middleware.guest.password.PasswordInformation;
import com.rccl.middleware.guest.password.exceptions.GuestAccountLockedException;
import com.rccl.middleware.guest.password.exceptions.GuestAuthenticationException;
import com.rccl.middleware.guest.password.exceptions.GuestNotFoundException;
import com.rccl.middleware.guest.password.exceptions.InvalidEmailException;
import com.rccl.middleware.guest.password.exceptions.InvalidPasswordException;
import com.rccl.middleware.guest.password.exceptions.InvalidPasswordTokenException;
import com.rccl.middleware.saviynt.api.SaviyntService;
import com.rccl.middleware.saviynt.api.exceptions.SaviyntExceptionFactory;
import com.rccl.middleware.saviynt.api.requests.SaviyntUpdatePassword;
import com.rccl.middleware.saviynt.api.requests.SaviyntUserToken;
import com.rccl.middleware.saviynt.api.requests.WebShopperAccount;
import com.rccl.middleware.saviynt.api.responses.AccountStatus;
import com.rccl.ops.common.logging.RcclLoggerFactory;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
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
    public HeaderServiceCall<ForgotPassword, ResponseBody> forgotPassword(String email) {
        return (requestHeader, request) -> {
            
            LOGGER.info("Processing forgot-password request for email : " + email);
            
            guestAccountPasswordValidator.validateForgotPasswordFields(request, email);
            
            return saviyntService.getAccountStatus(email, "email", "True").invoke()
                    .exceptionally(throwable -> {
                        Throwable cause = throwable.getCause();
                        if (cause instanceof SaviyntExceptionFactory.InvalidEmailFormatException) {
                            throw new InvalidEmailException();
                        }
                        
                        if (cause instanceof SaviyntExceptionFactory.ExistingGuestException) {
                            throw new GuestNotFoundException();
                        }
                        
                        throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                    })
                    .thenCompose(accountStatus -> {
                        if (StringUtils.isNotBlank(accountStatus.getVdsId())) {
                            return this.executeVDSUserForgotPasswordEmail(accountStatus, request, email);
                        } else if ("NeedsToBeMigrated".equals(accountStatus.getMessage())) {
                            return this.executeWebShopperForgotPasswordEmail(request, email);
                        } else {
                            throw new GuestNotFoundException();
                        }
                    });
        };
    }
    
    @Override
    public HeaderServiceCall<ForgotPasswordToken, ResponseBody> validateForgotPasswordToken() {
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
                                + request.getFirstName() + "|"
                                + request.getLastName() + "|"
                                + request.getWebShopperUsername())
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
                            Pair.create(ResponseHeader.OK, ResponseBody.<NotUsed>builder()
                                    .status(ResponseHeader.OK.status())
                                    .build()));
        };
    }
    
    @Override
    public HeaderServiceCall<PasswordInformation, ResponseBody<JsonNode>> updatePassword() {
        return (requestHeader, request) -> {
            
            LOGGER.info("processing update-password request");
            
            guestAccountPasswordValidator.validateAccountPasswordFields(request);
            
            final SaviyntUpdatePassword savinyntPassword = this.mapAttributesToSaviynt(request);
            
            // if request token is not empty, execute validateTokenUpdatePassword Saviynt service.
            if (StringUtils.isNotBlank(savinyntPassword.getToken())) {
                return saviyntService.updateAccountPasswordWithToken().invoke(savinyntPassword)
                        .exceptionally(throwable -> {
                            Throwable cause = throwable.getCause();
                            if (cause instanceof SaviyntExceptionFactory.ExistingGuestException
                                    || cause instanceof SaviyntExceptionFactory.NoSuchGuestException) {
                                throw new GuestNotFoundException();
                            } else if (cause instanceof SaviyntExceptionFactory.InvalidEmailFormatException) {
                                throw new InvalidEmailException();
                            } else if (cause instanceof SaviyntExceptionFactory.InvalidPasswordFormatException) {
                                throw new InvalidPasswordException();
                            } else if (cause instanceof SaviyntExceptionFactory.InvalidUserTokenException) {
                                throw new InvalidPasswordTokenException();
                            }
                            
                            throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                        })
                        .thenCompose(response -> this.authenticateUser(request));
                
            } else {
                return saviyntService.updateAccountPasswordWithQuestionAndAnswer().invoke(savinyntPassword)
                        .exceptionally(throwable -> {
                            Throwable cause = throwable.getCause();
                            if (cause instanceof SaviyntExceptionFactory.ExistingGuestException
                                    || cause instanceof SaviyntExceptionFactory.NoSuchGuestException) {
                                throw new GuestNotFoundException();
                            } else if (cause instanceof SaviyntExceptionFactory.InvalidEmailFormatException) {
                                throw new InvalidEmailException();
                            } else if (cause instanceof SaviyntExceptionFactory.InvalidPasswordFormatException) {
                                throw new InvalidPasswordException();
                            } else if (cause instanceof SaviyntExceptionFactory.AccountLockedException) {
                                throw new GuestAccountLockedException();
                            }
                            
                            throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                        })
                        .thenCompose(response -> {
                            if (response.getRemainingAttempts() != null) {
                                ObjectNode responseJson = OBJECT_MAPPER.createObjectNode();
                                responseJson.put("message", "Invalid Security Question and Answer.");
                                responseJson.put("remainingAttempts", response.getRemainingAttempts());
                                return CompletableFuture.completedFuture(
                                        Pair.create(ResponseHeader.OK.withStatus(400), ResponseBody
                                                .<JsonNode>builder()
                                                .status(400).payload(responseJson)
                                                .build()));
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
     * @param pwd the {@link PasswordInformation} request.
     * @return {@link CompletionStage}
     */
    private CompletionStage<Pair<ResponseHeader, ResponseBody<JsonNode>>> authenticateUser(PasswordInformation pwd) {
        if (pwd.getHeader() != null
                && "web".equals(pwd.getHeader().getChannel())) {
            return CompletableFuture.completedFuture(Pair.create(ResponseHeader.OK, ResponseBody
                    .<JsonNode>builder()
                    .status(ResponseHeader.OK.status())
                    .payload(OBJECT_MAPPER.createObjectNode())
                    .build()));
        }
        ForgeRockCredentials forgeRockCredentials = ForgeRockCredentials.builder()
                .username(pwd.getEmail())
                .password(pwd.getPassword())
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
                .thenApply(mobileTokens -> {
                    ObjectNode jsonResponse = OBJECT_MAPPER.createObjectNode();
                    jsonResponse.put("accountLoginStatus", LoginStatusEnum.NEW_ACCOUNT_AUTHENTICATED.value())
                            .put("accessToken", mobileTokens.getAccessToken())
                            .put("refreshToken", mobileTokens.getRefreshToken())
                            .put("openIdToken", mobileTokens.getIdToken())
                            .put("tokenExpiration", mobileTokens.getExpiration());
                    
                    OpenIdTokenInformation decryptedInfo = ForgeRockJWTDecoder
                            .decodeJwtToken(mobileTokens.getIdToken(), OpenIdTokenInformation.class);
                    
                    if (decryptedInfo != null) {
                        jsonResponse.put("vdsId", decryptedInfo.getVdsId())
                                .put("firstName", decryptedInfo.getFirstName())
                                .put("lastName", decryptedInfo.getLastName())
                                .put("email", decryptedInfo.getEmail())
                                .put("birthdate", decryptedInfo.getBirthdate());
                    }
                    
                    return Pair.create(ResponseHeader.OK, ResponseBody
                            .<JsonNode>builder()
                            .status(ResponseHeader.OK.status())
                            .payload(jsonResponse)
                            .build());
                });
    }
    
    /**
     * Prepares and executes an persistent entity event request for a VDS User version of forgot password email.
     *
     * @param status  {@link AccountStatus} Saviynt response object from AccountStatus service call.
     * @param request {@link ForgotPassword} from forgotPassword service call.
     * @param email   the email address of the user.
     * @return {@link NotUsed}
     */
    private CompletionStage<Pair<ResponseHeader, ResponseBody>> executeVDSUserForgotPasswordEmail(
            AccountStatus status, ForgotPassword request, String email) {
        CompletionStage<JsonNode> aemEmailTemplateFuture =
                aemService.getResetPasswordEmail().invoke()
                        .exceptionally(throwable -> {
                            LOGGER.error("Error occurred while retrieving AEM reset password email template.");
                            throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                        });
        
        return saviyntService
                .getGuestAccount("email", Optional.of(email), Optional.empty())
                .invoke()
                .exceptionally(throwable -> {
                    LOGGER.error("Error occurred while retrieving guest account "
                            + "details for email : " + email);
                    
                    Throwable cause = throwable.getCause();
                    
                    if (cause instanceof SaviyntExceptionFactory.ExistingGuestException
                            || cause instanceof SaviyntExceptionFactory.NoSuchGuestException) {
                        throw new GuestNotFoundException();
                        
                    } else if (cause instanceof SaviyntExceptionFactory.InvalidEmailFormatException) {
                        throw new InvalidEmailException();
                    }
                    
                    throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                    
                }).thenCombineAsync(aemEmailTemplateFuture, (saviyntResponse, aemResponse) -> {
                    
                    StringBuilder resetPasswordURL = new StringBuilder(request.getLink());
                    
                    // pass VDS ID and user token parameters for reset password if it's returned
                    // from forgotPasswordAccountStatus response.
                    if (status.getVdsId() != null && status.getToken() != null) {
                        resetPasswordURL.append("?vdsId=").append(status.getVdsId())
                                .append("&username=").append(email)
                                .append("&token=").append(status.getToken());
                    }
                    
                    LOGGER.info("Sending email notification to reset password");
                    
                    persistentEntityRegistry.refFor(EmailNotificationEntity.class, email)
                            .ask(new EmailNotificationCommand.SendEmailNotification(
                                    EmailNotification.builder()
                                            .recipient(email)
                                            .sender("notifications@rccl.com")
                                            .subject("Reset your My Cruises password")
                                            .content(this.composeEmailContent(saviyntResponse.getGuest().getFirstName(),
                                                    email, aemResponse, resetPasswordURL.toString()))
                                            .build()
                            ));
                    
                    return Pair.create(ResponseHeader.OK, ResponseBody.<NotUsed>builder()
                            .status(ResponseHeader.OK.status())
                            .build());
                });
    }
    
    /**
     * Prepares and executes a persistent entity event request for a WebShopper User version of forgot password email.
     *
     * @param request {@link ForgotPassword} from forgotPassword service call.
     * @param email   the email address of the user.
     * @return {@link NotUsed}
     */
    private CompletionStage<Pair<ResponseHeader, ResponseBody>> executeWebShopperForgotPasswordEmail(
            ForgotPassword request, String email) {
        CompletionStage<JsonNode> aemEmailTemplateFuture =
                aemService.getResetPasswordEmailMigration().invoke()
                        .exceptionally(throwable -> {
                            LOGGER.error("Error occurred while retrieving AEM reset password email template.");
                            throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                        });
        
        WebShopperAccount shopperAccount = WebShopperAccount.builder().userIdentifier(email).build();
        
        return saviyntService.getWebShopperPasswordToken()
                .invoke(shopperAccount)
                .exceptionally(throwable -> {
                    Throwable cause = throwable.getCause();
                    
                    if (cause instanceof SaviyntExceptionFactory.ExistingGuestException
                            || cause instanceof SaviyntExceptionFactory.NoSuchGuestException) {
                        throw new GuestNotFoundException();
                    } else if (cause instanceof SaviyntExceptionFactory.InvalidEmailFormatException) {
                        throw new InvalidEmailException();
                    }
                    
                    throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500), throwable);
                })
                .thenCombineAsync(aemEmailTemplateFuture, (saviyntResponse, aemResponse) -> {
                    
                    String resetPasswordURL = request.getLink()
                            + "?webShopperId=" + saviyntResponse.getShopperId()
                            + "&webShopperUserName=" + saviyntResponse.getLoginUsername()
                            + "&firstName=" + saviyntResponse.getFirstName()
                            + "&lastName=" + saviyntResponse.getLastName()
                            + "&token=" + saviyntResponse.getToken();
                    
                    persistentEntityRegistry.refFor(EmailNotificationEntity.class, email)
                            .ask(new EmailNotificationCommand.SendEmailNotification(
                                    EmailNotification.builder()
                                            .recipient(email)
                                            .sender("notifications@rccl.com")
                                            .subject("Reset your My Cruises password")
                                            .content(this.composeEmailContent(saviyntResponse.getFirstName(),
                                                    email, aemResponse, resetPasswordURL))
                                            .build()
                            ));
                    
                    return Pair.create(ResponseHeader.OK, ResponseBody.<NotUsed>builder()
                            .status(ResponseHeader.OK.status())
                            .build());
                });
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
                .password(new String(passwordInformation.getPassword()))
                .token(passwordInformation.getToken())
                .build();
    }
    
    /**
     * Replaces the email content variables with the proper guest attributes.
     *
     * @param firstName    the guest's first name.
     * @param email        the guest's email address
     * @param aemResponse  AEM reset password email responses
     * @param resetLinkURL service consumer provided URL appended with TOKEN from Saviynt
     * @return {@link String} Email Content
     */
    private String composeEmailContent(String firstName, String email, JsonNode aemResponse, String resetLinkURL) {
        String emailContent = aemResponse.findValue("data").get("text").asText();
        
        return StringUtils.replaceEach(
                emailContent,
                new String[]{"<first name>", "<guest username/email>", "<link to reset>"},
                new String[]{firstName, email, resetLinkURL}
        );
    }
}
