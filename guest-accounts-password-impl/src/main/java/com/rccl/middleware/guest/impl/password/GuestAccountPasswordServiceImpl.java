package com.rccl.middleware.guest.impl.password;

import akka.NotUsed;
import akka.cluster.MemberStatus;
import akka.japi.Pair;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.transport.RequestHeader;
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.server.HeaderServiceCall;
import com.rccl.middleware.akka.clustermanager.AkkaClusterManager;
import com.rccl.middleware.akka.clustermanager.models.ActorSystemInformation;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;
import com.rccl.middleware.common.logging.RcclLoggerFactory;
import com.rccl.middleware.common.response.ResponseBody;
import com.rccl.middleware.common.validation.MiddlewareValidation;
import com.rccl.middleware.guest.authentication.AccountCredentials;
import com.rccl.middleware.guest.authentication.GuestAuthenticationService;
import com.rccl.middleware.guest.impl.password.email.EmailNotificationEntity;
import com.rccl.middleware.guest.impl.password.email.EmailNotificationTag;
import com.rccl.middleware.guest.impl.password.email.PasswordUpdatedConfirmationEmail;
import com.rccl.middleware.guest.impl.password.email.ResetPasswordEmail;
import com.rccl.middleware.guest.password.ForgotPassword;
import com.rccl.middleware.guest.password.ForgotPasswordToken;
import com.rccl.middleware.guest.password.GuestAccountPasswordService;
import com.rccl.middleware.guest.password.PasswordInformation;
import com.rccl.middleware.guest.password.email.EmailNotification;
import com.rccl.middleware.guest.password.exceptions.GuestAccountLockedException;
import com.rccl.middleware.guest.password.exceptions.GuestNotFoundException;
import com.rccl.middleware.guest.password.exceptions.InvalidEmailException;
import com.rccl.middleware.guest.password.exceptions.InvalidPasswordException;
import com.rccl.middleware.guest.password.exceptions.InvalidPasswordTokenException;
import com.rccl.middleware.guest.password.exceptions.InvalidSecurityQuestionAndAnswerException;
import com.rccl.middleware.saviynt.api.SaviyntService;
import com.rccl.middleware.saviynt.api.exceptions.SaviyntExceptionFactory;
import com.rccl.middleware.saviynt.api.requests.SaviyntUpdatePassword;
import com.rccl.middleware.saviynt.api.requests.SaviyntUserToken;
import com.rccl.middleware.saviynt.api.requests.WebShopperAccount;
import com.rccl.middleware.saviynt.api.responses.AccountStatus;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.net.ConnectException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static com.rccl.middleware.guest.password.exceptions.GuestPasswordErrorCodeConstants.CONSTRAINT_VIOLATION;
import static com.rccl.middleware.guest.password.exceptions.GuestPasswordErrorCodeConstants.UNKNOWN_ERROR;

public class GuestAccountPasswordServiceImpl implements GuestAccountPasswordService {
    
    private static final String APPKEY_HEADER = "AppKey";
    
    private static final String DEFAULT_APP_KEY = ConfigFactory.load().getString("apigee.default.appkey");
    
    private static final Logger LOGGER = RcclLoggerFactory.getLogger(GuestAccountPasswordServiceImpl.class);
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private final AkkaClusterManager akkaClusterManager;
    
    private final GuestAccountPasswordValidator guestAccountPasswordValidator;
    
    private final GuestAuthenticationService guestAuthenticationService;
    
    private final SaviyntService saviyntService;
    
    private final PersistentEntityRegistry persistentEntityRegistry;
    
    private final ResetPasswordEmail resetPasswordEmail;
    
    private final PasswordUpdatedConfirmationEmail passwordUpdatedConfirmationEmail;
    
    @Inject
    public GuestAccountPasswordServiceImpl(AkkaClusterManager akkaClusterManager,
                                           GuestAuthenticationService guestAuthenticationService,
                                           SaviyntService saviyntService,
                                           GuestAccountPasswordValidator guestAccountPasswordValidator,
                                           PersistentEntityRegistry persistentEntityRegistry,
                                           ResetPasswordEmail resetPasswordEmail,
                                           PasswordUpdatedConfirmationEmail passwordUpdatedConfirmationEmail) {
        this.akkaClusterManager = akkaClusterManager;
        
        this.saviyntService = saviyntService;
        this.guestAccountPasswordValidator = guestAccountPasswordValidator;
        this.guestAuthenticationService = guestAuthenticationService;
        
        this.persistentEntityRegistry = persistentEntityRegistry;
        persistentEntityRegistry.register(EmailNotificationEntity.class);
        
        this.resetPasswordEmail = resetPasswordEmail;
        this.passwordUpdatedConfirmationEmail = passwordUpdatedConfirmationEmail;
    }
    
    @Override
    public HeaderServiceCall<ForgotPassword, ResponseBody> forgotPassword(String email) {
        return (requestHeader, request) -> {
            
            LOGGER.info("Processing forgot-password request for email : " + email);
            
            guestAccountPasswordValidator.validateForgotPasswordFields(request, email);
            
            return saviyntService.getAccountStatus(email, "email", "True").invoke()
                    .exceptionally(throwable -> {
                        LOGGER.error("An error occurred while retrieving Account Status.", throwable);
                        Throwable cause = throwable.getCause();
                        
                        if (cause instanceof ConnectException
                                || cause instanceof SaviyntExceptionFactory.SaviyntEnvironmentException) {
                            throw new MiddlewareTransportException(TransportErrorCode.ServiceUnavailable,
                                    throwable.getMessage(), UNKNOWN_ERROR);
                        }
                        
                        if (cause instanceof SaviyntExceptionFactory.InvalidEmailFormatException) {
                            throw new InvalidEmailException();
                        }
                        
                        if (cause instanceof SaviyntExceptionFactory.NoSuchGuestException) {
                            throw new GuestNotFoundException();
                        }
                        
                        throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500),
                                throwable.getMessage(), UNKNOWN_ERROR);
                    })
                    .thenCompose(accountStatus -> {
                        if (StringUtils.isNotBlank(accountStatus.getVdsId())) {
                            return this.executeVDSUserForgotPasswordEmail(accountStatus, request, email, requestHeader);
                        } else if ("NeedsToBeMigrated".equals(accountStatus.getMessage())) {
                            return this.executeWebShopperForgotPasswordEmail(request, email, requestHeader);
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
            if (request.getVdsId() != null) {
                MiddlewareValidation.validateWithGroups(request, CONSTRAINT_VIOLATION,
                        ForgotPasswordToken.NewUserChecks.class);
                
                saviyntUserToken = SaviyntUserToken.builder()
                        .user(request.getEmail() + "|" + request.getVdsId())
                        .token(request.getToken())
                        .build();
                
            } else {
                MiddlewareValidation.validateWithGroups(request, CONSTRAINT_VIOLATION,
                        ForgotPasswordToken.WebShopperChecks.class);
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
                        LOGGER.error("An error occurred while validating user token.", throwable);
                        
                        Throwable cause = throwable.getCause();
                        if (cause instanceof ConnectException
                                || cause instanceof SaviyntExceptionFactory.SaviyntEnvironmentException) {
                            throw new MiddlewareTransportException(TransportErrorCode.ServiceUnavailable,
                                    throwable.getMessage(), UNKNOWN_ERROR);
                        }
                        
                        if (cause instanceof SaviyntExceptionFactory.InvalidUserTokenException) {
                            throw new InvalidPasswordTokenException();
                        } else if (cause instanceof SaviyntExceptionFactory.NoSuchGuestException) {
                            throw new GuestNotFoundException();
                        }
                        
                        throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500),
                                throwable.getMessage(), UNKNOWN_ERROR);
                    })
                    .thenApply(notUsed ->
                            Pair.create(ResponseHeader.OK, ResponseBody.<NotUsed>builder().build()));
        };
    }
    
    @Override
    public HeaderServiceCall<PasswordInformation, ResponseBody<JsonNode>> updatePassword() {
        return (requestHeader, request) -> {
            
            LOGGER.info("processing update-password request");
            
            guestAccountPasswordValidator.validateAccountPasswordFields(request);
            
            final SaviyntUpdatePassword saviyntPassword = this.mapAttributesToSaviynt(request);
            
            CompletionStage<Pair<ResponseHeader, ResponseBody<JsonNode>>> stage;
            
            // if request token is not empty, execute validateTokenUpdatePassword Saviynt service.
            if (StringUtils.isNotBlank(saviyntPassword.getToken())) {
                stage = saviyntService.updateAccountPasswordWithToken().invoke(saviyntPassword)
                        .exceptionally(throwable -> {
                            LOGGER.error("An error occurred when updating password with token", throwable);
                            Throwable cause = throwable.getCause();
                            
                            if (cause instanceof ConnectException
                                    || cause instanceof SaviyntExceptionFactory.SaviyntEnvironmentException) {
                                throw new MiddlewareTransportException(TransportErrorCode.ServiceUnavailable,
                                        throwable.getMessage(), UNKNOWN_ERROR);
                            } else if (cause instanceof SaviyntExceptionFactory.NoSuchGuestException) {
                                throw new GuestNotFoundException();
                            } else if (cause instanceof SaviyntExceptionFactory.InvalidEmailFormatException) {
                                throw new InvalidEmailException();
                            } else if (cause instanceof SaviyntExceptionFactory.InvalidPasswordFormatException) {
                                throw new InvalidPasswordException();
                            } else if (cause instanceof SaviyntExceptionFactory.PasswordReuseException) {
                                throw new InvalidPasswordException(InvalidPasswordException.REUSE_ERROR);
                            } else if (cause instanceof SaviyntExceptionFactory.InvalidUserTokenException) {
                                throw new InvalidPasswordTokenException();
                            }
                            
                            throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500),
                                    throwable.getMessage(), UNKNOWN_ERROR);
                        })
                        .thenCompose(response -> this.authenticateUser(requestHeader, request));
                
            } else {
                stage = saviyntService.updateAccountPasswordWithQuestionAndAnswer().invoke(saviyntPassword)
                        .exceptionally(throwable -> {
                            LOGGER.error("An error occurred while trying to update password"
                                    + " with question and answer.", throwable);
                            Throwable cause = throwable.getCause();
                            
                            if (cause instanceof ConnectException
                                    || cause instanceof SaviyntExceptionFactory.SaviyntEnvironmentException) {
                                throw new MiddlewareTransportException(TransportErrorCode.ServiceUnavailable,
                                        throwable.getMessage(), UNKNOWN_ERROR);
                            }
                            
                            if (cause instanceof SaviyntExceptionFactory.NoSuchGuestException) {
                                throw new GuestNotFoundException();
                            } else if (cause instanceof SaviyntExceptionFactory.InvalidEmailFormatException) {
                                throw new InvalidEmailException();
                            } else if (cause instanceof SaviyntExceptionFactory.InvalidPasswordFormatException) {
                                throw new InvalidPasswordException();
                            } else if (cause instanceof SaviyntExceptionFactory.AccountLockedException) {
                                throw new GuestAccountLockedException();
                            } else if (cause instanceof SaviyntExceptionFactory.PasswordReuseException) {
                                throw new InvalidPasswordException(InvalidPasswordException.REUSE_ERROR);
                            } else if (cause
                                    instanceof SaviyntExceptionFactory.InvalidSecurityQuestionOrAnswerException) {
                                throw new InvalidSecurityQuestionAndAnswerException();
                            }
                            
                            throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500),
                                    throwable.getMessage(), UNKNOWN_ERROR);
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
                            
                            return this.authenticateUser(requestHeader, request);
                        });
            }
            
            return stage.thenApplyAsync(returnMe -> {
                passwordUpdatedConfirmationEmail.send(request, requestHeader);
                return returnMe;
            });
        };
    }
    
    @Override
    public HeaderServiceCall<NotUsed, ResponseBody<ActorSystemInformation>> akkaClusterHealthCheck() {
        return (requestHeader, notUsed) -> {
            ResponseHeader responseHeader;
            if (akkaClusterManager.getSelfStatus() == MemberStatus.up()) {
                LOGGER.info("Health Check - Akka self address {} with status: {}",
                        akkaClusterManager.getSelfAddress(), akkaClusterManager.getSelfStatus());
                responseHeader = ResponseHeader.OK;
            } else {
                LOGGER.info("Health Check - {} failed or is still trying to join the cluster.",
                        akkaClusterManager.getSelfAddress());
                responseHeader = ResponseHeader.OK.withStatus(503);
            }
            
            return CompletableFuture.completedFuture(Pair.create(responseHeader,
                    ResponseBody.<ActorSystemInformation>builder()
                            .payload(akkaClusterManager.getActorSystemInformation()).build()));
        };
    }
    
    @Override
    public Topic<EmailNotification> emailNotificationTopic() {
        return TopicProducer.singleStreamWithOffset(offset ->
                persistentEntityRegistry
                        .eventStream(EmailNotificationTag.EMAIL_NOTIFICATION_TAG, offset)
                        .map(pair -> {
                            LOGGER.debug("Publishing email notification message...");
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
     * Authenticates user ONLY if channel is either {@code app-ios} or {@code app-android}.
     * If the channel specified in the header is {@code web}, return immediately.
     *
     * @param pwd the {@link PasswordInformation} request.
     * @return {@link CompletionStage}
     */
    private CompletionStage<Pair<ResponseHeader, ResponseBody<JsonNode>>> authenticateUser(RequestHeader requestHeader,
                                                                                           PasswordInformation pwd) {
        if (pwd.getHeader() != null
                && "web".equals(pwd.getHeader().getChannel())) {
            return CompletableFuture.completedFuture(Pair.create(ResponseHeader.OK, ResponseBody
                    .<JsonNode>builder()
                    .payload(OBJECT_MAPPER.createObjectNode())
                    .build()));
        }
        AccountCredentials accountCredentials = AccountCredentials
                .builder()
                .header(pwd.getHeader())
                .username(pwd.getEmail())
                .password(pwd.getPassword())
                .build();
        
        final String appKey = requestHeader.getHeader(APPKEY_HEADER).orElse(DEFAULT_APP_KEY);
        
        return guestAuthenticationService.authenticateUser()
                .handleRequestHeader(rh -> rh.withHeader(APPKEY_HEADER, appKey))
                .invoke(accountCredentials)
                .exceptionally(throwable -> {
                    throw new MiddlewareTransportException(TransportErrorCode.InternalServerError,
                            throwable.getMessage(), UNKNOWN_ERROR);
                })
                .thenApply(jsonResponse ->
                        Pair.create(ResponseHeader.OK, ResponseBody
                                .<JsonNode>builder()
                                .payload(jsonResponse.getPayload())
                                .build()));
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
            AccountStatus status, ForgotPassword request, String email, RequestHeader requestHeader) {
        
        return saviyntService
                .getGuestAccount("email", Optional.of(email), Optional.empty())
                .invoke()
                .exceptionally(throwable -> {
                    LOGGER.error("Error occurred while retrieving guest account "
                            + "details for email : " + email);
                    
                    Throwable cause = throwable.getCause();
                    
                    if (cause instanceof ConnectException
                            || cause instanceof SaviyntExceptionFactory.SaviyntEnvironmentException) {
                        throw new MiddlewareTransportException(TransportErrorCode.ServiceUnavailable,
                                throwable.getMessage(), UNKNOWN_ERROR);
                    } else if (cause instanceof SaviyntExceptionFactory.ExistingGuestException
                            || cause instanceof SaviyntExceptionFactory.NoSuchGuestException) {
                        throw new GuestNotFoundException();
                        
                    } else if (cause instanceof SaviyntExceptionFactory.InvalidEmailFormatException) {
                        throw new InvalidEmailException();
                    }
                    
                    throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500),
                            throwable.getMessage(), UNKNOWN_ERROR);
                    
                }).thenApply(saviyntResponse -> {
                    StringBuilder resetPasswordUrl = new StringBuilder(request.getLink());
                    
                    // pass VDS ID and user token parameters for reset password if it's returned
                    // from forgotPasswordAccountStatus response.
                    if (status.getVdsId() != null && status.getToken() != null) {
                        resetPasswordUrl.append("?vdsId=").append(status.getVdsId())
                                .append("&username=").append(email)
                                .append("&token=").append(status.getToken());
                    }
                    
                    resetPasswordEmail.send(request,
                            email,
                            saviyntResponse.getGuest().getFirstName(),
                            resetPasswordUrl.toString(), requestHeader);
                    
                    return Pair.create(ResponseHeader.OK, ResponseBody.builder().build());
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
            ForgotPassword request, String email, RequestHeader requestHeader) {
        WebShopperAccount shopperAccount = WebShopperAccount.builder().userIdentifier(email).build();
        
        return saviyntService.getWebShopperPasswordToken()
                .invoke(shopperAccount)
                .exceptionally(throwable -> {
                    LOGGER.error("An error occurred when trying to get WebShopper Password Token", throwable);
                    Throwable cause = throwable.getCause();
                    if (cause instanceof ConnectException
                            || cause instanceof SaviyntExceptionFactory.SaviyntEnvironmentException) {
                        throw new MiddlewareTransportException(TransportErrorCode.ServiceUnavailable,
                                throwable.getMessage(), UNKNOWN_ERROR);
                    } else if (cause instanceof SaviyntExceptionFactory.ExistingGuestException
                            || cause instanceof SaviyntExceptionFactory.NoSuchGuestException) {
                        throw new GuestNotFoundException();
                    } else if (cause instanceof SaviyntExceptionFactory.InvalidEmailFormatException) {
                        throw new InvalidEmailException();
                    }
                    
                    throw new MiddlewareTransportException(TransportErrorCode.fromHttp(500),
                            throwable.getMessage(), UNKNOWN_ERROR);
                })
                .thenApply(saviyntResponse -> {
                    String resetPasswordUrl = request.getLink()
                            + "?email=" + email
                            + "&webShopperId=" + saviyntResponse.getShopperId()
                            + "&webShopperUserName=" + saviyntResponse.getLoginUsername()
                            + "&firstName=" + saviyntResponse.getFirstName()
                            + "&lastName=" + saviyntResponse.getLastName()
                            + "&token=" + saviyntResponse.getToken();
                    
                    resetPasswordEmail.send(request,
                            email,
                            saviyntResponse.getFirstName(),
                            resetPasswordUrl, requestHeader);
                    
                    return Pair.create(ResponseHeader.OK, ResponseBody.builder().build());
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
                .password(passwordInformation.getPassword())
                .token(passwordInformation.getToken())
                .build();
    }
}
