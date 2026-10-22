package pl.piomin.services.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;

/**
 * The number must be a multiple of {@link #value()}; {@code null} is valid.
 * Bean Validation has no step constraint, so also add {@code @Schema(multipleOf = ...)} for the contract.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MultipleOf.Validator.class)
public @interface MultipleOf {

    String value();

    String message() default "must be a multiple of {value}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<MultipleOf, BigDecimal> {

        private BigDecimal step;

        @Override
        public void initialize(MultipleOf annotation) {
            step = new BigDecimal(annotation.value());
        }

        @Override
        public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
            return value == null || value.remainder(step).signum() == 0;
        }
    }
}
