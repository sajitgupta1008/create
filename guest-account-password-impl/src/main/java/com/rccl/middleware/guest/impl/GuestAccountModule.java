package com.rccl.middleware.guest.impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import com.rccl.middleware.guest.impl.password.GuestAccountPasswordServiceImpl;
import com.rccl.middleware.guest.password.GuestAccountPasswordService;
import com.rccl.middleware.saviynt.api.SaviyntService;

public class GuestAccountModule extends AbstractModule implements ServiceGuiceSupport {
    
    @Override
    protected void configure() {
        bindService(GuestAccountPasswordService.class, GuestAccountPasswordServiceImpl.class);
        bindClient(SaviyntService.class);
    }
}
