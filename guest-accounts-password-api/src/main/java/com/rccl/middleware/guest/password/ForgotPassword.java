package com.rccl.middleware.guest.password;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotEmpty;

@Value
@Builder
public class ForgotPassword {
    
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Email(regexp = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$",
            message = "The email is invalidly formatted.")
    String email;
    
    @NotEmpty(message = "A reset password link is required.")
    String link;
}
