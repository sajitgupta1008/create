package com.rccl.middleware.guest.impl.password.email;

import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;

public class EmailNotificationTag {
    
    public static final AggregateEventTag<EmailNotificationEvent> EMAIL_NOTIFICATION_TAG =
            AggregateEventTag.of(EmailNotificationEvent.class, "EmailNotificationEvent");
}
