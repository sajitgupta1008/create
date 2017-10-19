package com.rccl.middleware.guest.password;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rccl.middleware.common.validation.validator.ValidatorConstants;
import lombok.Builder;
import lombok.Value;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForgotPassword {
    
    @NotEmpty(message = "An email is required.")
    @Size(min = 5, max = 100, message = "The email can have a minimum of 5 characters and "
            + "a maximum of 100 characters.")
    @Pattern(regexp = ValidatorConstants.EMAIL_REGEXP, message = "The email is invalidly formatted.")
    String email;
    
    @NotEmpty(message = "A reset password link is required.")
    @Pattern(regexp = "\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]",
            message = "The URL is invalidly formatted.")
    String link;
}
