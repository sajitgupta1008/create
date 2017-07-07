package com.rccl.middleware.guest.password;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lightbend.lagom.serialization.Jsonable;
import com.rccl.middleware.common.validation.validator.GuestAccountPassword;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import org.hibernate.validator.constraints.NotBlank;

import javax.validation.constraints.NotNull;

/**
 * Container of attributes used for password related services.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PasswordInformation implements Jsonable {
    
    @NotNull(message = "A password is required.")
    @GuestAccountPassword
    char[] password;
    
    @NotBlank(message = "A user token is required.")
    String token;
    
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @NotBlank(message = "A VDS ID is required.")
    String vdsId;
    
}
