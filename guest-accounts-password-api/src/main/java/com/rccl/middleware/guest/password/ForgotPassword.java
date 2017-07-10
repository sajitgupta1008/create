package com.rccl.middleware.guest.password;

import lombok.Builder;
import lombok.Value;
import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Value
@Builder
public class ForgotPassword {
    
    @NotEmpty(message = "An email is required.")
    @Size(min = 5, max = 100, message = "The email can only have up to 100 characters.")
    @Email(regexp = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$",
            message = "The email is invalidly formatted.")
    String email;
    
    @NotEmpty(message = "A reset password link is required.")
    @Pattern(regexp = "\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]",
            message = "The URL is invalidly formatted.")
    String link;
}
