package com.rccl.middleware.guest.password;

import lombok.Builder;
import lombok.Value;
import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import javax.validation.groups.Default;

@Builder
@Value
public class ForgotPasswordToken {
    
    @NotEmpty(message = "An email is required.", groups = NewUserChecks.class)
    @Size(min = 5, max = 256, message = "The email can only have up to 256 characters.", groups = NewUserChecks.class)
    @Email(regexp = "(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\""
            + "(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])"
            + "*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]"
            + "|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:"
            + "[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])",
            message = "The email is invalidly formatted.", groups = NewUserChecks.class)
    String email;
    
    @NotEmpty(message = "A VDS ID is required.", groups = NewUserChecks.class)
    String vdsId;
    
    @NotEmpty(message = "A Shopper ID is required.", groups = WebShopperChecks.class)
    @Pattern(regexp = "\\d+", message = "A Shopper ID must be numeric.", groups = WebShopperChecks.class)
    String webShopperId;
    
    @NotEmpty(message = "A WebShopper username is required.", groups = WebShopperChecks.class)
    String webShopperUserName;
    
    @NotEmpty(message = "A first name is required.", groups = WebShopperChecks.class)
    String firstName;
    
    @NotEmpty(message = "A last name is required.", groups = WebShopperChecks.class)
    String lastName;
    
    @NotEmpty(message = "A token is required.", groups = DefaultChecks.class)
    String token;
    
    public interface NewUserChecks {
        // Validation group interface.
    }
    
    public interface WebShopperChecks {
        // Validation group interface.
    }
    
    public interface DefaultChecks extends Default {
        // Validation group interface.
    }
}
