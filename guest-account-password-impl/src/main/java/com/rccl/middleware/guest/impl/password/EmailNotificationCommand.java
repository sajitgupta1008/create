package com.rccl.middleware.guest.impl.password;

import akka.Done;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.lightbend.lagom.serialization.CompressedJsonable;
import com.lightbend.lagom.serialization.Jsonable;
import com.rccl.middleware.guest.password.EmailNotification;
import lombok.Builder;
import lombok.Value;

public interface EmailNotificationCommand extends Jsonable {
    
    @Builder
    @Value
    final class SendEmailNotification implements EmailNotificationCommand, CompressedJsonable, PersistentEntity.ReplyType<Done> {
        
        final EmailNotification emailNotification;
    }
}
