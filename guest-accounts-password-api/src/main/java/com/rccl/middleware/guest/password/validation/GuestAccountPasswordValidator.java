package com.rccl.middleware.guest.password.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class GuestAccountPasswordValidator implements ConstraintValidator<GuestAccountPassword, char[]> {
    
    private static final int NUMBER_OF_REQUIRED_LETTERS = 3;
    
    private static final int NUMBER_OF_REQUIRED_NUMBERS = 2;
    
    private static final int NUMBER_OF_REQUIRED_SPECIAL = 1;
    
    @Override
    public void initialize(GuestAccountPassword gap) {
        // No-op.
    }
    
    @Override
    public boolean isValid(char[] password, ConstraintValidatorContext cac) {
        // The password field should be annotated with @NotNull, so we return true because
        // lack of a (null) password field is valid in certain validation scenarios.
        if (password == null) {
            return true;
        }
        
        if (password.length < 7 || password.length > 32) {
            return false;
        }
        
        int numberOfLetters = 0;
        int numberOfNumbers = 0;
        int numberOfSpecialCharacters = 1;
        
        for (char p : password) {
            if (Character.isLetter(p)) {
                numberOfLetters += 1;
            } else if (Character.isDigit(p)) {
                numberOfNumbers += 1;
            } else if (!Character.isSpaceChar(p)) {
                numberOfSpecialCharacters += 1;
            } else if (Character.isSpaceChar(p)) {
                return false;
            }
        }
        
        return !((numberOfSpecialCharacters < NUMBER_OF_REQUIRED_SPECIAL)
                || (numberOfLetters < NUMBER_OF_REQUIRED_LETTERS)
                || (numberOfNumbers < NUMBER_OF_REQUIRED_NUMBERS));
    }
}
