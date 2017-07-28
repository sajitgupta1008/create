package com.rccl.middleware.guest.impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import com.rccl.middleware.aem.api.AemService;
import com.rccl.middleware.forgerock.api.ForgeRockService;
import com.rccl.middleware.guest.impl.password.GuestAccountPasswordServiceImpl;
import com.rccl.middleware.guest.password.GuestAccountPasswordService;
import com.rccl.middleware.saviynt.api.SaviyntService;
import com.rccl.ops.common.logging.LogLevelUpdateScheduler;

public class GuestAccountModule extends AbstractModule implements ServiceGuiceSupport {
    
    @Override
    protected void configure() {
        bindService(GuestAccountPasswordService.class, GuestAccountPasswordServiceImpl.class);
        bindClient(ForgeRockService.class);
        bindClient(SaviyntService.class);
        bindClient(AemService.class);
        bind(LogLevelUpdateScheduler.class).asEagerSingleton();
    }
}
