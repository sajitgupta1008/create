package com.rccl.middleware.guest.password;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lightbend.lagom.serialization.Jsonable;
import com.rccl.middleware.guest.password.validation.Brand;
import com.rccl.middleware.guest.password.validation.GuestAccountPassword;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import org.hibernate.validator.constraints.Email;

import javax.validation.constraints.NotNull;

/**
 * Container of attributes used for password related services.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PasswordInformation implements Jsonable {
    
    @Brand
    Character brand;
    
    @NotNull(message = "A password is required.")
    @GuestAccountPassword
    char[] password;
    
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Email(regexp = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$",
            message = "The email is invalidly formatted.")
    String email;
}
