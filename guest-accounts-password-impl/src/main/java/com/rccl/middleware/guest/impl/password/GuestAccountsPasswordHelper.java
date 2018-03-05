package com.rccl.middleware.guest.impl.password;

import akka.NotUsed;
import akka.japi.Pair;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.transport.RequestHeader;
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;
import com.rccl.middleware.common.logging.RcclLoggerFactory;
import com.rccl.middleware.common.response.ResponseBody;
import com.rccl.middleware.guest.authentication.GuestAuthenticationService;
import com.rccl.middleware.guest.authentication.requests.AccountCredentials;
import com.rccl.middleware.guest.impl.password.email.PasswordUpdatedConfirmationEmail;
import com.rccl.middleware.guest.impl.password.email.ResetPasswordEmail;
import com.rccl.middleware.guest.password.ForgotPassword;
import com.rccl.middleware.guest.password.ForgotPasswordToken;
import com.rccl.middleware.guest.password.PasswordInformation;
import com.rccl.middleware.guest.password.exceptions.GuestAccountLockedException;
import com.rccl.middleware.guest.password.exceptions.GuestNotFoundException;
import com.rccl.middleware.guest.password.exceptions.InvalidEmailException;
import com.rccl.middleware.guest.password.exceptions.InvalidPasswordException;
import com.rccl.middleware.guest.password.exceptions.InvalidPasswordTokenException;
import com.rccl.middleware.guest.password.exceptions.InvalidSecurityQuestionAndAnswerException;
import com.rccl.middleware.saviynt.api.SaviyntService;
import com.rccl.middleware.saviynt.api.exceptions.SaviyntExceptionFactory;
import com.rccl.middleware.saviynt.api.jwt.SaviyntDecodedToken;
import com.rccl.middleware.saviynt.api.jwt.SaviyntJWTDecoder;
import com.rccl.middleware.saviynt.api.jwt.VDSDecodedJWTToken;
import com.rccl.middleware.saviynt.api.jwt.WebShopperDecodedJWTToken;
import com.rccl.middleware.saviynt.api.requests.SaviyntUpdatePassword;
import com.rccl.middleware.saviynt.api.requests.SaviyntUserToken;
import com.rccl.middleware.saviynt.api.requests.WebShopperAccount;
import com.rccl.middleware.saviynt.api.responses.AccountStatus;
import com.rccl.middleware.vds.VDSService;
import com.rccl.middleware.vds.exceptions.VDSExceptionFactory;
import com.rccl.middleware.vds.responses.WebShopperView;
import com.rccl.middleware.vds.responses.WebShopperViewList;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.lang3.StringUtils;

import java.net.ConnectException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static com.rccl.middleware.guest.password.exceptions.GuestPasswordErrorCodeConstants.UNKNOWN_ERROR;

public class GuestAccountsPasswordHelper {
    
    private static final String APPKEY_HEADER = "AppKey";
    
    private static final String DEFAULT_APP_KEY = ConfigFactory.load().getString("apigee.default.appkey");
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private static final Logger LOGGER = RcclLoggerFactory.getLogger(GuestAccountsPasswordHelper.class);
    
    private final SaviyntService saviyntService;
    
    private final ResetPasswordEmail resetPasswordEmail;
    
    private final GuestAuthenticationService guestAuthenticationService;
    
    private final PasswordUpdatedConfirmationEmail passwordUpdatedConfirmationEmail;
    
    private final VDSService vdsService;
    
    @Inject
    public GuestAccountsPasswordHelper(SaviyntService saviyntService,
                                       ResetPasswordEmail resetPasswordEmail,
                                       GuestAuthenticationService guestAuthenticationService,
                                       PasswordUpdatedConfirmationEmail passwordUpdatedConfirmationEmail,
                                       VDSService vdsService) {
        this.saviyntService = saviyntService;
        this.vdsService = vdsService;
        this.resetPasswordEmail = resetPasswordEmail;
        this.guestAuthenticationService = guestAuthenticationService;
        this.passwordUpdatedConfirmationEmail = passwordUpdatedConfirmationEmail;
    }
    
    /**
     * Invokes {@code Saviynt AccountStatus API} to determine if the email address provided
     * is a VDS or a WebShopper account. This will be used to determine the type of email to send.
     *
     * @param request the {@link ForgotPassword} request object.
     * @param rh      the {@link RequestHeader} from ForgotPassword service invocation.
     * @param email   the email address specified in the path URL request.
     * @return {@link CompletionStage}<{@link Pair}<{@link ResponseHeader}, {@link ResponseBody}>>
     */
    protected CompletionStage<Pair<ResponseHeader, ResponseBody>> processForgotPasswordEmail(ForgotPassword request,
                                                                                             RequestHeader rh,
                                                                                             String email) {
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
                        return this.executeVDSUserForgotPasswordEmail(accountStatus, request, email, rh);
                    } else if ("NeedsToBeMigrated".equals(accountStatus.getMessage())) {
                        return this.executeWebShopperForgotPasswordEmail(request, email, rh);
                    } else {
                        throw new GuestNotFoundException();
                    }
                });
    }
    
    /**
     * Invokes {@code Saviynt ValidateUserToken API} to determine the validity of the temporary password token
     * specified in the request.
     *
     * @param saviyntUserToken the {@link SaviyntUserToken} request object.
     * @return {@link CompletionStage}<{@link Pair}<{@link ResponseHeader}, {@link ResponseBody}>>
     */
    protected CompletionStage<Pair<ResponseHeader, ResponseBody>> processValidateForgotPasswordToken(
            SaviyntUserToken saviyntUserToken) {
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
    }
    
    /**
     * Invokes either {@code Saviynt UpdateAccountPasswordWithToken API}
     * or {@code Saviynt UpdateAccountPasswordWithQuestionAndAnswer API} depending on the attributes specified in
     * {@link PasswordInformation} object.
     * <p>
     * Upon a successful process, the account will be automatically logged in using {@code ForgeRock ROPC}
     * and a password update email confirmation will also be sent out.
     *
     * @param request       the {@link PasswordInformation} request object.
     * @param requestHeader the {@link RequestHeader} from service invocation.
     * @return {@link CompletionStage}<{@link Pair}<{@link ResponseHeader}, {@link ResponseBody}<{@link JsonNode}>>>
     */
    protected CompletionStage<Pair<ResponseHeader, ResponseBody<JsonNode>>> processUpdatePassword(
            PasswordInformation request, RequestHeader requestHeader) {
        
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
    }
    
    /**
     * Parses the token and populates {@link SaviyntUserToken} with the values gathered from
     * the decoded JWT.
     *
     * @param request the {@link ForgotPasswordToken} from service request.
     * @return {@link SaviyntUserToken}
     * @throws InvalidPasswordTokenException if there's a mismatch with decoded token values VS request values.
     * @throws MiddlewareTransportException  if something goes wrong during the decoding process.
     */
    protected SaviyntUserToken populatePropertiesFromToken(ForgotPasswordToken request) {
        try {
            SaviyntDecodedToken decodedToken = SaviyntJWTDecoder
                    .decodeJwtToken(request.getToken(), SaviyntDecodedToken.class);
            
            if (StringUtils.isNotBlank(request.getVdsId())) {
                VDSDecodedJWTToken vdsDecodedJWTToken = new VDSDecodedJWTToken(decodedToken.getPipedValue());
                
                if (vdsDecodedJWTToken.getVdsId().equals(request.getVdsId())) {
                    return SaviyntUserToken.builder()
                            .user(vdsDecodedJWTToken.getEmail() + "|"
                                    + vdsDecodedJWTToken.getVdsId())
                            .token(request.getToken())
                            .build();
                } else {
                    throw new InvalidPasswordTokenException();
                }
            } else {
                // for WebShopper
                WebShopperDecodedJWTToken shopperDecodedJWTToken =
                        new WebShopperDecodedJWTToken(decodedToken.getPipedValue());
                
                if (shopperDecodedJWTToken.getWebshopperId().equals(request.getWebShopperId())) {
                    return SaviyntUserToken.builder()
                            .user(shopperDecodedJWTToken.getWebshopperId() + "|"
                                    + shopperDecodedJWTToken.getFirstName() + "|"
                                    + shopperDecodedJWTToken.getLastName() + "|"
                                    + shopperDecodedJWTToken.getWebshopperUsername())
                            .token(request.getToken())
                            .build();
                } else {
                    throw new InvalidPasswordTokenException();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error occurred when decoding JWT Saviynt token.", e.getMessage());
            throw new InvalidPasswordTokenException();
        }
    }
    
    /**
     * Prepares and executes an persistent entity event request for a VDS User version of forgot password email.
     *
     * @param status  {@link AccountStatus} Saviynt response object from AccountStatus service call.
     * @param request {@link ForgotPassword} from forgotPassword service call.
     * @param email   the email address of the user.
     * @return {@link NotUsed}
     */
    protected CompletionStage<Pair<ResponseHeader, ResponseBody>> executeVDSUserForgotPasswordEmail(
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
    protected CompletionStage<Pair<ResponseHeader, ResponseBody>> executeWebShopperForgotPasswordEmail(
            ForgotPassword request, String email, RequestHeader requestHeader) {
        return this.getWebShopperViewFromEmail(email)
                .thenCompose(webshopper -> {
                    if (webshopper == null) {
                        throw new GuestNotFoundException();
                    }
                    
                    WebShopperAccount shopperAccount = WebShopperAccount.builder()
                            .userIdentifier(webshopper.getWebshopperId())
                            .propertyToSearch("customproperty15")
                            .build();
                    
                    LOGGER.debug("A webshopper was found. Proceeding with token generation.");
                    return saviyntService.getWebShopperPasswordToken()
                            .invoke(shopperAccount)
                            .exceptionally(throwable -> {
                                LOGGER.error("An error occurred when trying to get WebShopper Password Token",
                                        throwable);
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
                                
                                throw new MiddlewareTransportException(TransportErrorCode.UnexpectedCondition,
                                        throwable.getMessage(), UNKNOWN_ERROR);
                            })
                            .thenApply(saviyntResponse -> {
                                String resetPasswordUrl = request.getLink()
                                        + "?email=" + email
                                        + "&webShopperId=" + saviyntResponse.getShopperId()
                                        + "&token=" + saviyntResponse.getToken();
                                
                                resetPasswordEmail.send(request,
                                        email,
                                        saviyntResponse.getFirstName(),
                                        resetPasswordUrl, requestHeader);
                                
                                return Pair.create(ResponseHeader.OK, ResponseBody.builder().build());
                            });
                });
    }
    
    /**
     * Invokes {@code VDS Get WebShopper Attributes API} to retrieve {@link WebShopperViewList} based on the
     * given email address.
     * <p>
     * If the email address provided ends up with multiple webshopper accounts, it will try to compare the email
     * from the argument with the webshopper usernames in the {@link WebShopperViewList}. It will return the index
     * of the object if there is a match, otherwise, will return the first in the list.
     *
     * @param email the email address to look up.
     * @return {@link CompletionStage}<{@link WebShopperView}>
     */
    private CompletionStage<WebShopperView> getWebShopperViewFromEmail(String email) {
        return vdsService.getWebShopperAttributes("emailaddr=" + email).invoke()
                .exceptionally(throwable -> {
                    LOGGER.error("An error occurred when trying to get WebShopper attributes.", throwable);
                    Throwable cause = throwable.getCause();
                    if (cause instanceof ConnectException
                            || cause instanceof VDSExceptionFactory.GenericVDSException) {
                        throw new MiddlewareTransportException(TransportErrorCode.ServiceUnavailable,
                                throwable.getMessage(), UNKNOWN_ERROR);
                    }
                    
                    throw new MiddlewareTransportException(TransportErrorCode.UnexpectedCondition,
                            throwable.getMessage(), UNKNOWN_ERROR);
                })
                .thenApply(webShopperViewList -> {
                    WebShopperView webShopperView = null;
                    if (webShopperViewList != null && !webShopperViewList.getWebshopperViews().isEmpty()) {
                        List<WebShopperView> webshopperViews = webShopperViewList.getWebshopperViews();
                        if (webshopperViews.size() == 1) {
                            webShopperView = webshopperViews.get(0);
                        } else {
                            for (WebShopperView shopper : webShopperViewList.getWebshopperViews()) {
                                if (email.equalsIgnoreCase(shopper.getWebshopperUsername())) {
                                    webShopperView = shopper;
                                    break;
                                }
                            }
                            // get the first index if there aren't any match with webshopper username.
                            if (webShopperView == null) {
                                webShopperView = webshopperViews.get(0);
                            }
                        }
                    }
                    
                    return webShopperView;
                });
    }
    
    /**
     * Authenticates user ONLY if channel is either {@code app-ios} or {@code app-android}.
     * If the channel specified in the header is {@code web}, return immediately.
     *
     * @param pwd the {@link PasswordInformation} request.
     * @return {@link CompletionStage}
     */
    protected CompletionStage<Pair<ResponseHeader, ResponseBody<JsonNode>>> authenticateUser(
            RequestHeader requestHeader, PasswordInformation pwd) {
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
     * Sets all the necessary attribute values for password update in Saviynt model.
     *
     * @param passwordInformation {@link PasswordInformation}
     * @return {@code SaviyntGuest}
     */
    protected SaviyntUpdatePassword mapAttributesToSaviynt(PasswordInformation passwordInformation) {
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
