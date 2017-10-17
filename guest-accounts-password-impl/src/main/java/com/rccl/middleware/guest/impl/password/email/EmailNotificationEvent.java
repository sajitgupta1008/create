package com.rccl.middleware.guest.impl.password.email;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.serialization.Jsonable;
import com.rccl.middleware.guest.password.EmailNotification;
import lombok.Value;
import org.codehaus.jackson.annotate.JsonCreator;

public interface EmailNotificationEvent extends Jsonable, AggregateEvent<EmailNotificationEvent> {
    
    @Override
    default AggregateEventTag<EmailNotificationEvent> aggregateTag() {
        return EmailNotificationTag.EMAIL_NOTIFICATION_TAG;
    }
    
    EmailNotification getEmailNotification();
    
    @Value
    final class EmailNotificationPublished implements EmailNotificationEvent {
        
        public final EmailNotification emailNotification;
        
        @JsonCreator
        public EmailNotificationPublished(EmailNotification emailNotification) {
            this.emailNotification = emailNotification;
        }
    }
}
