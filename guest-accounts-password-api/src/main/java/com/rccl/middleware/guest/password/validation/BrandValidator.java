package com.rccl.middleware.guest.password.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class BrandValidator implements ConstraintValidator<Brand, Character> {
    
    @Override
    public void initialize(Brand brand) {
        // No-op.
    }
    
    @Override
    public boolean isValid(Character character, ConstraintValidatorContext constraintValidatorContext) {
        // The brand field should be annotated with @NotNull, so we return true because
        // lack of a (null) brand field is valid in certain validation scenarios.
        if (character == null) {
            return true;
        }
        
        switch (character) {
            case 'r':
            case 'R':
            case 'c':
            case 'C':
            case 'z':
            case 'Z':
                return true;
        }
        
        return false;
    }
}
