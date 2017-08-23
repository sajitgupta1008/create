package com.rccl.middleware.guest.impl.password;


import akka.Done;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.rccl.middleware.guest.password.EmailNotification;

import java.time.LocalDateTime;
import java.util.Optional;

public class EmailNotificationEntity extends
        PersistentEntity<EmailNotificationCommand, EmailNotificationEvent, EmailNotificationState> {
    
    @Override
    @SuppressWarnings("unchecked")
    public Behavior initialBehavior(Optional<EmailNotificationState> snapshotState) {
        
        BehaviorBuilder builder = newBehaviorBuilder(snapshotState.orElse(EmailNotificationState.emptyState()));
        
        builder.setCommandHandler(EmailNotificationCommand.SendEmailNotification.class, (cmd, commandContext) -> {
            EmailNotification cmdNotification = cmd.getEmailNotification();
            EmailNotification emailNotification = EmailNotification.builder()
                    .recipient(cmdNotification.getRecipient())
                    .sender(cmdNotification.getSender())
                    .subject(cmdNotification.getSubject())
                    .content(cmdNotification.getContent())
                    .build();
            
            return commandContext.thenPersist(new EmailNotificationEvent.EmailNotificationPublished(emailNotification),
                    event -> commandContext.reply(Done.getInstance()));
        });
        
        builder.setEventHandler(EmailNotificationEvent.EmailNotificationPublished.class,
                evt -> new EmailNotificationState(evt.emailNotification, LocalDateTime.now().toString()));
        
        return builder.build();
    }
}
