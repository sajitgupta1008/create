package com.rccl.middleware.guest.password;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lightbend.lagom.serialization.Jsonable;
import com.rccl.middleware.common.header.Header;
import com.rccl.middleware.common.validation.validator.GuestAccountPassword;
import lombok.Builder;
import lombok.Value;
import org.hibernate.validator.constraints.Email;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.groups.Default;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PasswordInformation implements Jsonable {
    
    @NotNull(message = "A header is required", groups = DefaultChecks.class)
    @Valid
    Header header;
    
    @NotNull(message = "A password is required.", groups = DefaultChecks.class)
    @GuestAccountPassword
    char[] password;
    
    @NotNull(message = "A VDS ID is required.", groups = TokenChecks.class)
    String vdsId;
    
    @NotNull(message = "An email is required.", groups = DefaultChecks.class)
    @Email(regexp = "(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\""
            + "(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])"
            + "*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]"
            + "|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:"
            + "[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])",
            message = "The email is invalidly formatted.",
            groups = DefaultChecks.class)
    String email;
    
    @NotNull(message = "A token is required.", groups = TokenChecks.class)
    String token;
    
    @NotNull(message = "A security question is required.", groups = QuestionAnswerChecks.class)
    String securityQuestion;
    
    @NotNull(message = "A security answer is required.", groups = QuestionAnswerChecks.class)
    String securityAnswer;
    
    public interface QuestionAnswerChecks {
        // Validation group interface.
    }
    
    public interface TokenChecks {
        // Validation group interface.
    }
    
    public interface DefaultChecks extends Default {
        // Validation group interface.
    }
}
