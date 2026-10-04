package com.taskmanagement.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.*;
import java.nio.charset.StandardCharsets;

@Documented
@Constraint(validatedBy = Utf8Length.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Utf8Length {
    String message() default "must contain at most {max} UTF-8 bytes";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    int max();

    class Validator implements ConstraintValidator<Utf8Length, String> {
        private int max;

        @Override
        public void initialize(Utf8Length constraint) {
            max = constraint.max();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.getBytes(StandardCharsets.UTF_8).length <= max;
        }
    }
}
