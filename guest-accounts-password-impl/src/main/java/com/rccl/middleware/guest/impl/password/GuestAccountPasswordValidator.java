package com.rccl.middleware.guest.impl.password;


import com.rccl.middleware.common.validation.MiddlewareValidation;
import com.rccl.middleware.common.validation.MiddlewareValidationException;
import com.rccl.middleware.guest.password.ForgotPassword;
import com.rccl.middleware.guest.password.PasswordInformation;
import org.apache.commons.lang3.StringUtils;

import static com.rccl.middleware.guest.password.exceptions.GuestPasswordErrorCodeConstants.CONSTRAINT_VIOLATION;

public class GuestAccountPasswordValidator {
    
    /**
     * Validate the given {@link PasswordInformation}
     * <p>
     * If an attribute is invalid, an unhandled {@link MiddlewareValidationException} is thrown. Otherwise,
     * nothing happens.
     *
     * @param passwordInformation {@link PasswordInformation}
     */
    public void validateAccountPasswordFields(PasswordInformation passwordInformation) {
        if (StringUtils.isNotBlank(passwordInformation.getToken())) {
            MiddlewareValidation.validateWithGroups(passwordInformation, CONSTRAINT_VIOLATION,
                    PasswordInformation.TokenChecks.class);
        } else {
            MiddlewareValidation.validateWithGroups(passwordInformation, CONSTRAINT_VIOLATION,
                    PasswordInformation.QuestionAnswerChecks.class);
        }
    }
    
    /**
     * Validate the given {@link ForgotPassword}
     * <p>
     * If the attribute is invalid, an unhandled {@link MiddlewareValidationException} is thrown. Otherwise,
     * nothing happens.
     *
     * @param forgotPassword {@link ForgotPassword}
     * @param email          {@code String}
     */
    public void validateForgotPasswordFields(ForgotPassword forgotPassword, String email) {
        MiddlewareValidation.validate(ForgotPassword.builder()
                .header(forgotPassword.getHeader())
                .email(email)
                .link(forgotPassword.getLink())
                .build(), CONSTRAINT_VIOLATION);
    }
}
