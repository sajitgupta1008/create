package com.rccl.middleware.guest.password;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lightbend.lagom.serialization.Jsonable;
import com.rccl.middleware.common.header.Header;
import com.rccl.middleware.common.validation.validator.GuestAccountPassword;
import com.rccl.middleware.common.validation.validator.ValidatorConstants;
import lombok.Builder;
import lombok.Value;
import org.hibernate.validator.constraints.Email;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.validation.groups.Default;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PasswordInformation implements Jsonable {
    
    @Valid
    Header header;
    
    @NotNull(message = "A password is required.", groups = DefaultChecks.class)
    @GuestAccountPassword
    char[] password;
    
    @NotNull(message = "A VDS ID is required.", groups = TokenChecks.class)
    String vdsId;
    
    @NotNull(message = "An email is required.", groups = DefaultChecks.class)
    @Size(min = 5, max = 256, message = "The email can only have up to 256 characters.", groups = DefaultChecks.class)
    @Email(regexp = ValidatorConstants.EMAIL_REGEXP, groups = DefaultChecks.class)
    String email;
    
    @NotNull(message = "A token is required.", groups = TokenChecks.class)
    String token;
    
    @NotNull(message = "A security question is required.", groups = QuestionAnswerChecks.class)
    String securityQuestion;
    
    @NotNull(message = "A security answer is required.", groups = QuestionAnswerChecks.class)
    String securityAnswer;
    
    public interface QuestionAnswerChecks extends DefaultChecks {
        // Validation group interface.
    }
    
    public interface TokenChecks extends DefaultChecks {
        // Validation group interface.
    }
    
    public interface DefaultChecks extends Default {
        // Validation group interface.
    }
}
