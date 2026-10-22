package pl.piomin.services.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** {@code availableFrom} must not be after {@code availableTo}; the error is reported on {@code availableTo}. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DateRange.Validator.class)
public @interface DateRange {

    String message() default "must not be before availableFrom";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<DateRange, AvailabilityWindow> {

        @Override
        public boolean isValid(AvailabilityWindow window, ConstraintValidatorContext context) {
            if (window.availableFrom() == null || window.availableTo() == null
                    || !window.availableFrom().isAfter(window.availableTo())) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("availableTo").addConstraintViolation();
            return false;
        }
    }
}
