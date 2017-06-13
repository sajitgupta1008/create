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
     * @param email               {@code String}
     */
    public void validateAccountPasswordFields(PasswordInformation passwordInformation, String email) {
        
        final PasswordInformation infoWithEmail = PasswordInformation.builder()
                .email(email)
                .brand(passwordInformation.getBrand())
                .password(passwordInformation.getPassword())
                .build();
        
        MiddlewareValidation.validate(infoWithEmail);
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
        final ForgotPassword forgotPasswordWithEmail = ForgotPassword.builder()
                .email(email)
                .link(forgotPassword.getLink())
                .build();
        
        MiddlewareValidation.validate(forgotPasswordWithEmail);
    }
}
