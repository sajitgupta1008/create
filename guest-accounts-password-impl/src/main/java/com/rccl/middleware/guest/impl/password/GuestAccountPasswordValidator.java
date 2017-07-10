package com.rccl.middleware.guest.impl.password;


import com.rccl.middleware.common.validation.MiddlewareValidation;
import com.rccl.middleware.common.validation.MiddlewareValidationException;
import com.rccl.middleware.guest.password.ForgotPassword;
import com.rccl.middleware.guest.password.PasswordInformation;

public class GuestAccountPasswordValidator {
    
    /**
     * Validate the given {@link PasswordInformation}
     * <p>
     * If the attribute is invalid, an unhandled {@link MiddlewareValidationException} is thrown. Otherwise,
     * nothing happens.
     *
     * @param passwordInformation {@link PasswordInformation}
     * @param vdsId               {@code String}
     */
    public void validateAccountPasswordFields(PasswordInformation passwordInformation, String vdsId) {
        MiddlewareValidation.validate(PasswordInformation.builder()
                .vdsId(vdsId)
                .token(passwordInformation.getToken())
                .password(passwordInformation.getPassword())
                .build());
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
                .email(email)
                .link(forgotPassword.getLink())
                .build());
    }
}
