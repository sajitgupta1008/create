package com.rccl.middleware.guest.impl.password;

import akka.actor.ActorSystem;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import com.lightbend.lagom.javadsl.testkit.ServiceTest;
import com.rccl.middleware.aem.api.email.AemEmailService;
import com.rccl.middleware.aem.api.email.AemEmailServiceStub;
import com.rccl.middleware.common.header.Header;
import com.rccl.middleware.guest.authentication.GuestAuthenticationService;
import com.rccl.middleware.guest.authentication.GuestAuthenticationServiceStub;
import com.rccl.middleware.guest.password.ForgotPassword;
import com.rccl.middleware.guest.password.GuestAccountPasswordService;
import com.rccl.middleware.guest.password.PasswordInformation;
import com.rccl.middleware.guest.password.email.EmailNotification;
import com.rccl.middleware.saviynt.api.SaviyntService;
import com.rccl.middleware.saviynt.api.SaviyntServiceImplStub;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.defaultSetup;
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.startServer;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertNotNull;
import static play.inject.Bindings.bind;

public class GuestAccountPasswordServiceMessageBrokerTest {
    
    private static final FiniteDuration TWENTY_SECONDS = new FiniteDuration(20, SECONDS);
    
    private static ActorSystem system;
    
    private static volatile ServiceTest.TestServer testServer;
    
    private static GuestAccountPasswordService service;
    
    @BeforeClass
    public static void beforeClass() {
        final ServiceTest.Setup setup = defaultSetup()
                .configureBuilder(builder -> builder.overrides(
                        bind(SaviyntService.class).to(SaviyntServiceImplStub.class),
                        bind(AemEmailService.class).to(AemEmailServiceStub.class),
                        bind(GuestAuthenticationService.class).to(GuestAuthenticationServiceStub.class)
                ));
        
        testServer = startServer(setup.withCassandra(true));
        service = testServer.client(GuestAccountPasswordService.class);
        system = ActorSystem.create();
    }
    
    @AfterClass
    public static void afterClass() {
        if (testServer != null) {
            testServer.stop();
            testServer = null;
        }
        
        system.terminate();
        system = null;
    }
    
    // TODO: Re-enable this logic and unit tests once the Email Communication story is re-approved.
    @Ignore
    @Test
    public void testEmailNotificationPublishOnUpdatePassword() throws InterruptedException, ExecutionException, TimeoutException {
        Source<EmailNotification, ?> source = service.emailNotificationTopic()
                .subscribe()
                .atMostOnceSource();
        
        Sink<EmailNotification, TestSubscriber.Probe<EmailNotification>> ts = TestSink.probe(testServer.system());
        TestSubscriber.Probe<EmailNotification> probe = source.runWith(ts, testServer.materializer());
        
        Header header = Header.builder().brand('R').channel("web").build();
        
        PasswordInformation pi = PasswordInformation.builder()
                .header(header)
                .vdsId("G1234567")
                .email("successful@domain.com")
                .password("juanalacubana1234".toCharArray())
                .securityQuestion("What was your dream job as a child?")
                .securityAnswer("Professional Water Distributor")
                .build();
        
        service.updatePassword()
                .invoke(pi)
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        
        EmailNotification en = probe.request(1).expectNext(TWENTY_SECONDS);
        assertNotNull(en);
        
        // Cleanup.
        probe.cancel();
    }
    
    @Test
    public void testEmailNotificationPublishOnForgotPassword() throws InterruptedException, ExecutionException, TimeoutException {
        Source<EmailNotification, ?> source = service.emailNotificationTopic()
                .subscribe()
                .atMostOnceSource();
        
        Sink<EmailNotification, TestSubscriber.Probe<EmailNotification>> ts = TestSink.probe(testServer.system());
        TestSubscriber.Probe<EmailNotification> probe = source.runWith(ts, testServer.materializer());
        
        Header header = Header.builder().brand('R').channel("web").build();
        
        ForgotPassword fp = ForgotPassword.builder()
                .header(header)
                .email("successful@domain.com")
                .link("http://www.juana.com")
                .build();
        
        service.forgotPassword("successful@domain.com")
                .invoke(fp)
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        
        EmailNotification en = probe.request(1).expectNext(TWENTY_SECONDS);
        assertNotNull(en);
        
        // Cleanup.
        probe.cancel();
    }
}

