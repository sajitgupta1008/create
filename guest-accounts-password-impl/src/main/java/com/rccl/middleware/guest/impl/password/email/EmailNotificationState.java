package com.rccl.middleware.guest.impl.password.email;

import com.lightbend.lagom.serialization.CompressedJsonable;
import com.rccl.middleware.guest.password.email.EmailNotification;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public final class EmailNotificationState implements CompressedJsonable {
    
    private static final long serialVersionUID = 1L;
    
    private final EmailNotification emailNotification;
    
    private final String timestamp;
    
    public static EmailNotificationState emptyState() {
        return new EmailNotificationState(EmailNotification.builder().build(), LocalDateTime.now().toString());
    }
}
