package com.rccl.middleware.guest.impl.password;


import com.rccl.middleware.guest.password.PasswordInformation;
import com.rccl.middleware.guest.password.exceptions.InvalidGuestPasswordException;

import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import javax.validation.groups.Default;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GuestAccountPasswordValidator {
    
    private final Validator validator;
    
    @Inject
    public GuestAccountPasswordValidator(Validator validator) {
        this.validator = validator;
    }
    
    /**
     * Validate the given {@link PasswordInformation}
     * <p>
     * If the attribute is invalid, an unhandled {@link InvalidGuestPasswordException} is thrown. Otherwise,
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
        
        Set<ConstraintViolation<PasswordInformation>> violations = validator.validate(infoWithEmail, Default.class);
        
        if (violations.isEmpty()) {
            return;
        }
        
        Map<String, String> violationsReport = violations.stream().collect(
                Collectors.toMap(
                        cv -> cv.getPropertyPath().toString(),
                        ConstraintViolation::getMessage,
                        (duplicate1, duplicate2) -> duplicate1
                )
        );
        
        throw new InvalidGuestPasswordException(violationsReport);
    }
    
    /**
     * Answers if an email is in some valid format.
     *
     * @param email {@code String}
     * @return {@code boolean} - {@code true} if valid.
     */
    public boolean isValidEmailFormat(String email) {
        String emailPattern = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@"
                + "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
        Pattern pattern = Pattern.compile(emailPattern);
        Matcher matcher = pattern.matcher(email);
        return matcher.matches();
    }
}
