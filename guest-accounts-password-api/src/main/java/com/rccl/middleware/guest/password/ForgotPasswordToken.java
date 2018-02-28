package com.rccl.middleware.guest.password;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rccl.middleware.common.validation.validator.ValidatorConstants;
import lombok.Builder;
import lombok.Value;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import javax.validation.groups.Default;

@Builder
@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForgotPasswordToken {
    
    @NotEmpty(message = "An email is required.", groups = NewUserChecks.class)
    @Size(min = 5, max = 100, message = "The email can have a minimum of 5 characters and "
            + "a maximum of 100 characters.", groups = NewUserChecks.class)
    @Pattern(regexp = ValidatorConstants.EMAIL_REGEXP,
            message = "The email is invalidly formatted.", groups = NewUserChecks.class)
    String email;
    
    @NotEmpty(message = "A VDS ID is required.", groups = NewUserChecks.class)
    @Pattern(regexp = "([GEC])\\d+", message = "The VDS ID is invalidly formatted.", groups = NewUserChecks.class)
    @Size(max = 9, message = "The VDS ID can have a maximum of 9 characters.", groups = NewUserChecks.class)
    String vdsId;
    
    @NotEmpty(message = "A Shopper ID is required.", groups = WebShopperChecks.class)
    @Pattern(regexp = "\\d+", message = "A Shopper ID must be numeric.", groups = WebShopperChecks.class)
    String webShopperId;
    
    @NotEmpty(message = "A token is required.", groups = DefaultChecks.class)
    String token;
    
    public interface NewUserChecks extends DefaultChecks {
        // Validation group interface.
    }
    
    public interface WebShopperChecks extends DefaultChecks {
        // Validation group interface.
    }
    
    public interface DefaultChecks extends Default {
        // Validation group interface.
    }
}
