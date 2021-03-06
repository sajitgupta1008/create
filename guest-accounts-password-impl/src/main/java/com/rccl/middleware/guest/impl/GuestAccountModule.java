package com.rccl.middleware.guest.impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import com.rccl.middleware.aem.api.AemService;
import com.rccl.middleware.aem.api.email.AemEmailService;
import com.rccl.middleware.common.logging.LogLevelUpdateScheduler;
import com.rccl.middleware.guest.authentication.GuestAuthenticationService;
import com.rccl.middleware.guest.impl.password.GuestAccountPasswordServiceImpl;
import com.rccl.middleware.guest.password.GuestAccountPasswordService;
import com.rccl.middleware.notifications.NotificationsService;
import com.rccl.middleware.saviynt.api.SaviyntService;
import com.rccl.middleware.vds.VDSService;

public class GuestAccountModule extends AbstractModule implements ServiceGuiceSupport {
    
    @Override
    protected void configure() {
        bindService(GuestAccountPasswordService.class, GuestAccountPasswordServiceImpl.class);
        bindClient(GuestAuthenticationService.class);
        bindClient(SaviyntService.class);
        bindClient(VDSService.class);
        bindClient(AemService.class);
        bindClient(AemEmailService.class);
        bindClient(NotificationsService.class);
        bind(LogLevelUpdateScheduler.class).asEagerSingleton();
    }
}
