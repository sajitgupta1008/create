package com.rccl.middleware.guest.impl.password;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import com.fasterxml.jackson.databind.JsonNode;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.RequestHeader;
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader;
import com.lightbend.lagom.javadsl.server.HeaderServiceCall;
import com.lightbend.lagom.javadsl.testkit.ServiceTest;
import com.rccl.middleware.aem.api.AemService;
import com.rccl.middleware.aem.api.AemServiceImplStub;
import com.rccl.middleware.aem.api.email.AemEmailService;
import com.rccl.middleware.aem.api.email.AemEmailServiceStub;
import com.rccl.middleware.akka.clustermanager.models.ActorSystemInformation;
import com.rccl.middleware.common.header.Header;
import com.rccl.middleware.common.response.ResponseBody;
import com.rccl.middleware.common.validation.MiddlewareValidationException;
import com.rccl.middleware.guest.authentication.GuestAuthenticationService;
import com.rccl.middleware.guest.authentication.GuestAuthenticationServiceStub;
import com.rccl.middleware.guest.password.ForgotPassword;
import com.rccl.middleware.guest.password.ForgotPasswordToken;
import com.rccl.middleware.guest.password.GuestAccountPasswordService;
import com.rccl.middleware.guest.password.PasswordInformation;
import com.rccl.middleware.guest.password.exceptions.InvalidPasswordTokenException;
import com.rccl.middleware.notifications.EmailNotification;
import com.rccl.middleware.notifications.NotificationsService;
import com.rccl.middleware.saviynt.api.SaviyntService;
import com.rccl.middleware.saviynt.api.SaviyntServiceImplStub;
import com.rccl.middleware.saviynt.api.exceptions.SaviyntExceptionFactory;
import com.rccl.middleware.vds.VDSService;
import com.rccl.middleware.vds.VDSServiceStub;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.defaultSetup;
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.startServer;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static play.inject.Bindings.bind;

public class GuestAccountPasswordServiceTest {
    
    private static ActorSystem system;
    
    private static volatile ServiceTest.TestServer testServer;
    
    private static GuestAccountPasswordService guestAccountPasswordService;
    
    @BeforeClass
    public static void setUp() {
        final ServiceTest.Setup setup = defaultSetup()
                .configureBuilder(builder -> builder.overrides(
                        bind(GuestAuthenticationService.class).to(GuestAuthenticationServiceStub.class),
                        bind(SaviyntService.class).to(SaviyntServiceImplStub.class),
                        bind(VDSService.class).to(VDSServiceStub.class),
                        bind(AemService.class).to(AemServiceImplStub.class),
                        bind(AemEmailService.class).to(AemEmailServiceStub.class),
                        bind(GuestAccountPasswordService.class).to(GuestAccountPasswordServiceImpl.class),
                        bind(NotificationsService.class).to(NotificationsServiceStub.class)
                ));
        
        testServer = startServer(setup.withCassandra(true));
        guestAccountPasswordService = testServer.client(GuestAccountPasswordService.class);
        
        system = ActorSystem.create();
    }
    
    @AfterClass
    public static void tearDown() {
        if (testServer != null) {
            testServer.stop();
            testServer = null;
        }
        
        system.terminate();
        system = null;
    }
    
    @Test
    public void testSuccessfulPostForgotPassword() throws Exception {
        HeaderServiceCall<ForgotPassword, ResponseBody> forgotPasswordService =
                (HeaderServiceCall<ForgotPassword, ResponseBody>) guestAccountPasswordService
                        .forgotPassword("successful@domain.com");
        
        Pair<ResponseHeader, ResponseBody> result = forgotPasswordService
                .invokeWithHeaders(RequestHeader.DEFAULT, createSampleForgotPassword())
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        
        assertTrue("Should return a status 200.", result.first().status() == 200);
    }
    
    @Test(expected = MiddlewareValidationException.class)
    public void testFailureForgotPasswordForInvalidEmail() throws Exception {
        HeaderServiceCall<ForgotPassword, ResponseBody> forgotPasswordService =
                (HeaderServiceCall<ForgotPassword, ResponseBody>) guestAccountPasswordService
                        .forgotPassword("jsmith@rccl");
        
        Pair<ResponseHeader, ResponseBody> result = forgotPasswordService
                .invokeWithHeaders(RequestHeader.DEFAULT, createSampleForgotPassword())
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        
        assertNotNull("The test should throw MiddlewareValidationException.", result.second());
        
    }
    
    @Test(expected = SaviyntExceptionFactory.NoSuchGuestException.class)
    public void shouldFailForgottenPasswordForNonExistingAccount() throws Exception {
        HeaderServiceCall<ForgotPassword, ResponseBody> forgotPasswordService =
                (HeaderServiceCall<ForgotPassword, ResponseBody>) guestAccountPasswordService
                        .forgotPassword("random@email.com");
        
        Pair<ResponseHeader, ResponseBody> result = forgotPasswordService
                .invokeWithHeaders(RequestHeader.DEFAULT, createSampleForgotPassword())
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        
        assertNotNull("The test should throw ExecutionException.", result.second());
    }
    
    @Test
    public void testSuccessfulVDSForgotPasswordTokenValidation() throws Exception {
        ForgotPasswordToken forgotPasswordToken = ForgotPasswordToken.builder()
                .email("successful@domain.com")
                .vdsId("G1234567")
                .token("imaginethisisatoken")
                .build();
        
        HeaderServiceCall<ForgotPasswordToken, ResponseBody> tokenValidationService =
                (HeaderServiceCall<ForgotPasswordToken, ResponseBody>) guestAccountPasswordService
                        .validateForgotPasswordToken();
        
        Pair<ResponseHeader, ResponseBody> result = tokenValidationService
                .invokeWithHeaders(RequestHeader.DEFAULT, forgotPasswordToken)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        assertTrue("Response status must be 200.", result.first().status() == 200);
    }
    
    @Test
    public void testSuccessfulWebShopperForgotPasswordTokenValidation() throws Exception {
        ForgotPasswordToken forgotPasswordToken = ForgotPasswordToken.builder()
                .webShopperId("12345678")
                .token("eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ7XCJJRFwiOlwiMTIzNDU2Nzh8Zmlyc3R8bGFzdHx1c"
                        + "2VybmFtZVwifSIsImV4cCI6MTUxOTkyNjEwOX0.rxiV0mN8MaRDBnzycSKX7TdLJj2ng46zhbZYEJPTcxs")
                .build();
        
        HeaderServiceCall<ForgotPasswordToken, ResponseBody> tokenValidationService =
                (HeaderServiceCall<ForgotPasswordToken, ResponseBody>) guestAccountPasswordService
                        .validateForgotPasswordToken();
        
        Pair<ResponseHeader, ResponseBody> result = tokenValidationService
                .invokeWithHeaders(RequestHeader.DEFAULT, forgotPasswordToken)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        assertTrue("Response status must be 200.", result.first().status() == 200);
    }
    
    @Test(expected = MiddlewareValidationException.class)
    public void testFailureValidationOfForgotPasswordTokenValidation() throws Exception {
        ForgotPasswordToken forgotPasswordToken = ForgotPasswordToken.builder()
                .email("@domain.com")
                .vdsId("G")
                .token("imaginethisisatoken")
                .build();
        
        HeaderServiceCall<ForgotPasswordToken, ResponseBody> tokenValidationService =
                (HeaderServiceCall<ForgotPasswordToken, ResponseBody>) guestAccountPasswordService
                        .validateForgotPasswordToken();
        
        tokenValidationService.invokeWithHeaders(RequestHeader.DEFAULT, forgotPasswordToken)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        assertTrue("Must return a MiddlewareValidationException instead.", false);
        
    }
    
    @Test(expected = SaviyntExceptionFactory.InvalidUserTokenException.class)
    public void testFailureForgotPasswordTokenValidation() throws Exception {
        ForgotPasswordToken forgotPasswordToken = ForgotPasswordToken.builder()
                .email("failure@domain.com")
                .vdsId("G1111111")
                .token("imaginethisisatoken")
                .build();
        
        HeaderServiceCall<ForgotPasswordToken, ResponseBody> tokenValidationService =
                (HeaderServiceCall<ForgotPasswordToken, ResponseBody>) guestAccountPasswordService
                        .validateForgotPasswordToken();
        
        tokenValidationService.invokeWithHeaders(RequestHeader.DEFAULT, forgotPasswordToken)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        assertTrue("Must return a Saviynt exception instead.", false);
    }
    
    @Test(expected = InvalidPasswordTokenException.class)
    public void testFailureForgotPasswordTokenValidationForWebShopper() throws Exception {
        // Should throw an exception for webshopperId mismatch.
        ForgotPasswordToken forgotPasswordToken = ForgotPasswordToken.builder()
                .webShopperId("4567")
                .token("eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ7XCJJRFwiOlwiMTIzNHxGaXJzdE5hb"
                        + "WV8TGFzdE5hbWV8c2hvcHBlclVzZXJOYW1lMTIzXCJ9IiwiZXhwIjoxNTE5OTI2MTA5fQ.ZlIFw"
                        + "-7zgqVdfHYcJoO9Khw5hg_yDkYdp-n8Nriw9ls")
                .build();
        
        HeaderServiceCall<ForgotPasswordToken, ResponseBody> tokenValidationService =
                (HeaderServiceCall<ForgotPasswordToken, ResponseBody>) guestAccountPasswordService
                        .validateForgotPasswordToken();
        
        tokenValidationService
                .invokeWithHeaders(RequestHeader.DEFAULT, forgotPasswordToken)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        assertTrue("Must throw an exception instead.", false);
    }
    
    @Test
    public void shouldUpdatePasswordSuccessfully() throws Exception {
        PasswordInformation passwordInformation = PasswordInformation.builder()
                .header(Header.builder().brand('R').channel("app-ios").locale(Locale.US).build())
                .email("successful@domain.com").vdsId("G1234567")
                .password("password1".toCharArray()).token("thisisasampletoken").build();
        
        HeaderServiceCall<PasswordInformation, ResponseBody<JsonNode>> updatePasswordService =
                (HeaderServiceCall<PasswordInformation, ResponseBody<JsonNode>>) guestAccountPasswordService
                        .updatePassword();
        
        Pair<ResponseHeader, ResponseBody<JsonNode>> result = updatePasswordService
                .invokeWithHeaders(RequestHeader.DEFAULT, passwordInformation)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        
        assertNotNull("Response must not be empty/null.", result);
        assertTrue("Response message must be successful.", result.first().status() == 200);
    }
    
    @Test(expected = MiddlewareValidationException.class)
    public void shouldNotUpdatePasswordWithInvalidFields() throws Exception {
        PasswordInformation passwordInformation = PasswordInformation.builder()
                .password("123".toCharArray()).token("thisisasampletoken").build();
        
        HeaderServiceCall<PasswordInformation, ResponseBody<JsonNode>> updatePasswordService =
                (HeaderServiceCall<PasswordInformation, ResponseBody<JsonNode>>) guestAccountPasswordService
                        .updatePassword();
        
        Pair<ResponseHeader, ResponseBody<JsonNode>> result = updatePasswordService
                .invokeWithHeaders(RequestHeader.DEFAULT, passwordInformation)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        
        assertNotNull("This should fail and throw an exception instead.", result);
    }
    
    @Test(expected = SaviyntExceptionFactory.InvalidUserTokenException.class)
    public void shouldNotUpdatePasswordForNonExistingUser() throws Exception {
        PasswordInformation passwordInformation = PasswordInformation.builder()
                .header(Header.builder().brand('R').channel("app-ios").locale(Locale.US).build())
                .vdsId("G7654321").email("nonexisting@email.com")
                .password("password1".toCharArray()).token("thisisasampletoken").build();
        
        HeaderServiceCall<PasswordInformation, ResponseBody<JsonNode>> updatePasswordService =
                (HeaderServiceCall<PasswordInformation, ResponseBody<JsonNode>>) guestAccountPasswordService
                        .updatePassword();
        
        Pair<ResponseHeader, ResponseBody<JsonNode>> result = updatePasswordService
                .invokeWithHeaders(RequestHeader.DEFAULT, passwordInformation)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        
        assertTrue("This should fail and throw an exception instead.", result == null);
    }
    
    @Test
    public void testAkkaClusterHealthCheck() throws Exception {
        HeaderServiceCall<NotUsed, ResponseBody<ActorSystemInformation>> service =
                (HeaderServiceCall<NotUsed, ResponseBody<ActorSystemInformation>>) guestAccountPasswordService.akkaClusterHealthCheck();
        
        Pair<ResponseHeader, ResponseBody<ActorSystemInformation>> response = service
                .invokeWithHeaders(RequestHeader.DEFAULT, NotUsed.getInstance())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);
        
        ActorSystemInformation payload = response.second().getPayload();
        
        assertNotNull(payload.getActorSystemName());
        assertNotNull(payload.getSelfAddress());
        assertFalse(payload.getClusterMembers().isEmpty());
    }
    
    private ForgotPassword createSampleForgotPassword() {
        return ForgotPassword.builder()
                .header(Header.builder().channel("web").brand('R').build())
                .link("http://www.rccl.com/forgotPassword")
                .build();
    }
    
    private static class NotificationsServiceStub implements NotificationsService {
        
        @Override
        public ServiceCall<EmailNotification, ResponseBody> sendEmail() {
            return request -> CompletableFuture.completedFuture(ResponseBody.builder().payload("Email has been sent successfully.").build());
        }
    }
}
