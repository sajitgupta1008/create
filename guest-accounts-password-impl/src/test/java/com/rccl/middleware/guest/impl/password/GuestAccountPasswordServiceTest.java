package com.rccl.middleware.guest.impl.password;

import akka.Done;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import com.fasterxml.jackson.databind.JsonNode;
import com.lightbend.lagom.javadsl.api.transport.RequestHeader;
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader;
import com.lightbend.lagom.javadsl.server.HeaderServiceCall;
import com.lightbend.lagom.javadsl.testkit.PersistentEntityTestDriver;
import com.lightbend.lagom.javadsl.testkit.PersistentEntityTestDriver.Outcome;
import com.lightbend.lagom.javadsl.testkit.ServiceTest;
import com.rccl.middleware.aem.api.AemService;
import com.rccl.middleware.aem.api.AemServiceImplStub;
import com.rccl.middleware.common.header.Header;
import com.rccl.middleware.common.response.ResponseBody;
import com.rccl.middleware.common.validation.MiddlewareValidationException;
import com.rccl.middleware.guest.authentication.GuestAuthenticationService;
import com.rccl.middleware.guest.authentication.GuestAuthenticationServiceStub;
import com.rccl.middleware.guest.impl.password.email.EmailNotificationCommand;
import com.rccl.middleware.guest.impl.password.email.EmailNotificationEntity;
import com.rccl.middleware.guest.impl.password.email.EmailNotificationEvent;
import com.rccl.middleware.guest.impl.password.email.EmailNotificationState;
import com.rccl.middleware.guest.password.email.EmailNotification;
import com.rccl.middleware.guest.password.ForgotPassword;
import com.rccl.middleware.guest.password.ForgotPasswordToken;
import com.rccl.middleware.guest.password.GuestAccountPasswordService;
import com.rccl.middleware.guest.password.PasswordInformation;
import com.rccl.middleware.saviynt.api.SaviyntService;
import com.rccl.middleware.saviynt.api.SaviyntServiceImplStub;
import com.rccl.middleware.saviynt.api.exceptions.SaviyntExceptionFactory;
import org.apache.commons.lang3.StringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.defaultSetup;
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.startServer;
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.withServer;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static play.inject.Bindings.bind;

public class GuestAccountPasswordServiceTest {
    
    private static ActorSystem system;
    
    private static volatile ServiceTest.TestServer testServer;
    
    private static GuestAccountPasswordService guestAccountPasswordService;
    
    private static PersistentEntityTestDriver<EmailNotificationCommand, EmailNotificationEvent, EmailNotificationState> driver;
    
    @BeforeClass
    public static void setUp() {
        final ServiceTest.Setup setup = defaultSetup()
                .configureBuilder(builder -> builder.overrides(
                        bind(GuestAuthenticationService.class).to(GuestAuthenticationServiceStub.class),
                        bind(SaviyntService.class).to(SaviyntServiceImplStub.class),
                        bind(AemService.class).to(AemServiceImplStub.class),
                        bind(GuestAccountPasswordService.class).to(GuestAccountPasswordServiceImpl.class)
                ));
        
        testServer = startServer(setup.withCassandra(true));
        guestAccountPasswordService = testServer.client(GuestAccountPasswordService.class);
        
        system = ActorSystem.create();
        driver = new PersistentEntityTestDriver<>(system, new EmailNotificationEntity(), "email");
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
    
    @Test
    public void testEmailNotificationEntity() {
        EmailNotification emailNotificationSample = EmailNotification.builder()
                .recipient("successful@domain.com")
                .sender("sender@email.com")
                .content("hello world")
                .subject("test")
                .build();
        
        Outcome<EmailNotificationEvent, EmailNotificationState> outcome = driver.run(EmailNotificationCommand.SendEmailNotification
                .builder()
                .emailNotification(emailNotificationSample)
                .build());
        
        assertThat(outcome.events().get(0).getEmailNotification(), is(equalTo(emailNotificationSample)));
        assertThat(outcome.events().size(), is(equalTo(1)));
        assertThat(outcome.state().getEmailNotification(), is(equalTo(emailNotificationSample)));
        assertThat(outcome.getReplies().get(0), is(equalTo(Done.getInstance())));
        assertThat(outcome.issues().isEmpty(), is(true));
    }
    
    @Test
    public void testSuccessfulEmailNotificationPublishing() {
        final ServiceTest.Setup setup = defaultSetup()
                .configureBuilder(builder -> builder.overrides(
                        bind(SaviyntService.class).to(SaviyntServiceImplStub.class),
                        bind(AemService.class).to(AemServiceImplStub.class)
                )).withCassandra(true);
        
        withServer(setup, server -> {
            GuestAccountPasswordService client = server.client(GuestAccountPasswordService.class);
            Source<EmailNotification, ?> source = client.emailNotificationTopic().subscribe().atMostOnceSource();
            
            TestSubscriber.Probe<EmailNotification> probe = source
                    .runWith(
                            TestSink.probe(server.system()), server.materializer()
                    );
            
            client.forgotPassword("successful@domain.com")
                    .invoke(createSampleForgotPassword())
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            
            FiniteDuration finiteDuration = new FiniteDuration(20, SECONDS);
            EmailNotification actual = probe.request(1).expectNext(finiteDuration);
            
            assertTrue("Email sender must not be empty.", StringUtils.isNotEmpty(actual.getSender()));
            assertTrue("Email recipient must not be empty.", StringUtils.isNotEmpty(actual.getRecipient()));
            assertTrue("Email body must not be empty", StringUtils.isNotEmpty(actual.getContent()));
        });
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
    
    @Test(expected = SaviyntExceptionFactory.ExistingGuestException.class)
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
                .webShopperUsername("username")
                .firstName("first")
                .lastName("last")
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
    
    private ForgotPassword createSampleForgotPassword() {
        return ForgotPassword.builder().link("http://www.rccl.com/forgotPassword").build();
    }
}
