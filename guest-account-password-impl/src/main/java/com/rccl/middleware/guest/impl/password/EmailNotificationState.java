package com.rccl.middleware.guest.impl.password;


import com.lightbend.lagom.serialization.CompressedJsonable;
import com.rccl.middleware.guest.password.EmailNotification;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public final class EmailNotificationState implements CompressedJsonable {
    
    private final EmailNotification emailNotification;
    
    private final String timestamp;
    
    public static EmailNotificationState emptyState() {
        return new EmailNotificationState(EmailNotification.builder().build(), LocalDateTime.now().toString());
    }
}
