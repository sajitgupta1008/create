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
@Constraint(validatedBy = BrandValidator.class)
@Documented
public @interface Brand {
    
    Class<?>[] groups() default {};
    
    String message() default "The brand must be one of the following characters: r (R), c (C), or z (Z)";
    
    Class<? extends Payload>[] payload() default {};
}
