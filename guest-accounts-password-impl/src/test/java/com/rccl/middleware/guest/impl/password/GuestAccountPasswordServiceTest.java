package com.rccl.middleware.guest.impl.password;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.testkit.JavaTestKit;
import com.fasterxml.jackson.databind.JsonNode;
import com.lightbend.lagom.javadsl.api.transport.RequestHeader;
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader;
import com.lightbend.lagom.javadsl.server.HeaderServiceCall;
import com.lightbend.lagom.javadsl.testkit.PersistentEntityTestDriver;
import com.lightbend.lagom.javadsl.testkit.PersistentEntityTestDriver.Outcome;
import com.lightbend.lagom.javadsl.testkit.ServiceTest;
import com.rccl.middleware.common.validation.MiddlewareValidationException;
import com.rccl.middleware.guest.password.EmailNotification;
import com.rccl.middleware.guest.password.ForgotPassword;
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
                        bind(SaviyntService.class).to(SaviyntServiceImplStub.class),
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
        
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }
    
    @Test
    public void shouldPostForgotPasswordSuccessfully() throws Exception {
        HeaderServiceCall<ForgotPassword, NotUsed> forgotPasswordService =
                (HeaderServiceCall<ForgotPassword, NotUsed>) guestAccountPasswordService.forgotPassword("abc.xyz@domain123.com");
        
        Pair<ResponseHeader, NotUsed> result = forgotPasswordService
                .invokeWithHeaders(RequestHeader.DEFAULT, createSampleForgotPassword())
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        
        assertTrue("Should return a status 200.", result.first().status() == 200);
    }
    
    @Test
    public void shouldProcessEmailNotificationCQRS() {
        EmailNotification emailNotificationSample = EmailNotification.builder()
                .recipient("abc.xyz@domain123.com")
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
    public void shouldPublishEmailNotificationSuccessfully() {
        final ServiceTest.Setup setup = defaultSetup()
                .configureBuilder(builder -> builder.overrides(
                        bind(SaviyntService.class).to(SaviyntServiceImplStub.class)
                )).withCassandra(true);
        
        withServer(setup, server -> {
            GuestAccountPasswordService client = server.client(GuestAccountPasswordService.class);
            Source<EmailNotification, ?> source = client.emailNotificationTopic().subscribe().atMostOnceSource();
            
            TestSubscriber.Probe<EmailNotification> probe = source
                    .runWith(
                            TestSink.probe(server.system()), server.materializer()
                    );
            
            client.forgotPassword("abc.xyz@domain123.com")
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
    public void shouldFailForgottenPasswordForInvalidEmail() throws Exception {
        HeaderServiceCall<ForgotPassword, NotUsed> forgotPasswordService =
                (HeaderServiceCall<ForgotPassword, NotUsed>) guestAccountPasswordService.forgotPassword("jsmith@rccl");
        
        Pair<ResponseHeader, NotUsed> result = forgotPasswordService
                .invokeWithHeaders(RequestHeader.DEFAULT, createSampleForgotPassword())
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        
        assertNotNull("The test should throw MiddlewareValidationException.", result.second());
        
    }
    
    @Test(expected = SaviyntExceptionFactory.ExistingGuestException.class)
    public void shouldFailForgottenPasswordForNonExistingAccount() throws Exception {
        HeaderServiceCall<ForgotPassword, NotUsed> forgotPasswordService =
                (HeaderServiceCall<ForgotPassword, NotUsed>) guestAccountPasswordService.forgotPassword("random@email.com");
        
        Pair<ResponseHeader, NotUsed> result = forgotPasswordService
                .invokeWithHeaders(RequestHeader.DEFAULT, createSampleForgotPassword())
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        
        assertNotNull("The test should throw SaviyntExceptionFactory.ExistingGuestException.", result.second());
    }
    
    @Test
    public void shouldUpdatePasswordSuccessfully() throws Exception {
        String testEmailId = "successful@domain.com";
        PasswordInformation passwordInformation = PasswordInformation.builder()
                .brand('R').password("RCCL@1232".toCharArray()).build();
        
        HeaderServiceCall<PasswordInformation, JsonNode> updatePasswordService =
                (HeaderServiceCall<PasswordInformation, JsonNode>) guestAccountPasswordService.updatePassword(testEmailId);
        
        Pair<ResponseHeader, JsonNode> result = updatePasswordService
                .invokeWithHeaders(RequestHeader.DEFAULT, passwordInformation)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        
        assertNotNull("Response must not be empty/null.", result);
        assertTrue("Response message must be successful.", result.first().status() == 200);
    }
    
    @Test(expected = MiddlewareValidationException.class)
    public void shouldNotUpdatePasswordWithInvalidFields() throws Exception {
        String testEmailId = "invalidemail@domain.com2";
        PasswordInformation passwordInformation = PasswordInformation.builder()
                .brand('R').password("123".toCharArray()).build();
        
        HeaderServiceCall<PasswordInformation, JsonNode> updatePasswordService =
                (HeaderServiceCall<PasswordInformation, JsonNode>) guestAccountPasswordService.updatePassword(testEmailId);
        
        Pair<ResponseHeader, JsonNode> result = updatePasswordService
                .invokeWithHeaders(RequestHeader.DEFAULT, passwordInformation)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        
        assertNotNull("This should fail and throw an exception instead.", result);
    }
    
    @Test(expected = SaviyntExceptionFactory.NoSuchGuestException.class)
    public void shouldNotUpdatePasswordForNonExistingUser() throws Exception {
        String testEmailId = "failure@domain.com";
        PasswordInformation passwordInformation = PasswordInformation.builder()
                .brand('R').password("RCCL12345".toCharArray()).build();
        
        HeaderServiceCall<PasswordInformation, JsonNode> updatePasswordService =
                (HeaderServiceCall<PasswordInformation, JsonNode>) guestAccountPasswordService.updatePassword(testEmailId);
        
        Pair<ResponseHeader, JsonNode> result = updatePasswordService
                .invokeWithHeaders(RequestHeader.DEFAULT, passwordInformation)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        
        assertTrue("This should fail and throw an exception instead.", result == null);
    }
    
    private final ForgotPassword createSampleForgotPassword() {
        return ForgotPassword.builder().link("http://www.rccl.com/forgotPassword").build();
    }
}
