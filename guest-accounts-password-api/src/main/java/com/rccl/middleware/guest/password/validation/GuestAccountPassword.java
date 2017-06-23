package com.rccl.middleware.guest.password.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = GuestAccountPasswordValidator.class)
@Documented
public @interface GuestAccountPassword {
    
    Class<?>[] groups() default {};
    
    String message() default "The password must be between 7 and 32 characters, inclusive, "
            + "with at least three (3) letters, two (2) numbers, and one (1) special character.";
    
    Class<? extends Payload>[] payload() default {};
}
