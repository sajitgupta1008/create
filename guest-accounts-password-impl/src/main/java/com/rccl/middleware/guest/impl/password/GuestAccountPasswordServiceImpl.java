package com.rccl.middleware.guest.impl.password;

import akka.NotUsed;
import akka.cluster.MemberStatus;
import akka.japi.Pair;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.server.HeaderServiceCall;
import com.rccl.middleware.akka.clustermanager.AkkaClusterManager;
import com.rccl.middleware.akka.clustermanager.models.ActorSystemInformation;
import com.rccl.middleware.common.logging.RcclLoggerFactory;
import com.rccl.middleware.common.response.ResponseBody;
import com.rccl.middleware.common.validation.MiddlewareValidation;
import com.rccl.middleware.guest.impl.password.email.EmailNotificationEntity;
import com.rccl.middleware.guest.impl.password.email.EmailNotificationTag;
import com.rccl.middleware.guest.password.ForgotPassword;
import com.rccl.middleware.guest.password.ForgotPasswordToken;
import com.rccl.middleware.guest.password.GuestAccountPasswordService;
import com.rccl.middleware.guest.password.PasswordInformation;
import com.rccl.middleware.guest.password.email.EmailNotification;
import com.rccl.middleware.saviynt.api.requests.SaviyntUserToken;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

import static com.rccl.middleware.guest.password.exceptions.GuestPasswordErrorCodeConstants.CONSTRAINT_VIOLATION;

public class GuestAccountPasswordServiceImpl implements GuestAccountPasswordService {
    
    private static final Logger LOGGER = RcclLoggerFactory.getLogger(GuestAccountPasswordServiceImpl.class);
    
    private final AkkaClusterManager akkaClusterManager;
    
    private final GuestAccountPasswordValidator validator;
    
    private final GuestAccountsPasswordHelper helper;
    
    private final PersistentEntityRegistry persistentEntityRegistry;
    
    
    @Inject
    public GuestAccountPasswordServiceImpl(AkkaClusterManager akkaClusterManager,
                                           GuestAccountPasswordValidator validator,
                                           GuestAccountsPasswordHelper helper,
                                           PersistentEntityRegistry persistentEntityRegistry) {
        this.akkaClusterManager = akkaClusterManager;
        this.validator = validator;
        this.helper = helper;
        
        this.persistentEntityRegistry = persistentEntityRegistry;
        persistentEntityRegistry.register(EmailNotificationEntity.class);
    }
    
    @Override
    public HeaderServiceCall<ForgotPassword, ResponseBody> forgotPassword(String email) {
        return (requestHeader, request) -> {
            LOGGER.info("Processing forgot-password request for email: {}", email);
            validator.validateForgotPasswordFields(request, email);
            return helper.processForgotPasswordEmail(request, requestHeader, email);
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
                saviyntUserToken = helper.populatePropertiesFromToken(request);
            }
            
            return helper.processValidateForgotPasswordToken(saviyntUserToken);
        };
    }
    
    @Override
    public HeaderServiceCall<PasswordInformation, ResponseBody<JsonNode>> updatePassword() {
        return (requestHeader, request) -> {
            LOGGER.info("processing update-password request");
            validator.validateAccountPasswordFields(request);
            
            return helper.processUpdatePassword(request, requestHeader);
        };
    }
    
    @Override
    public HeaderServiceCall<NotUsed, ResponseBody<ActorSystemInformation>> akkaClusterHealthCheck() {
        return (requestHeader, notUsed) -> {
            ResponseHeader responseHeader;
            if (akkaClusterManager.getSelfStatus() == MemberStatus.up()) {
                LOGGER.debug("Health Check - Akka self address {} with status: {}",
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
}
