package ma.ceac.telereleve.meter.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Business rule: the serial prefix must match the energy type, CEAC-E-... for electricity and
 * CEAC-W-... for water. Malformed serials are left to the @Pattern constraint.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SerialMatchesType.Validator.class)
public @interface SerialMatchesType {

    String message() default "Le préfixe du numéro de série ne correspond pas au type (E pour électricité, W pour eau).";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<SerialMatchesType, MeterRequest> {

        @Override
        public boolean isValid(MeterRequest request, ConstraintValidatorContext context) {
            if (request == null || request.type() == null || request.serial() == null
                    || !request.serial().matches(MeterRequest.SERIAL_REGEX)) {
                return true;
            }
            if (request.serial().charAt(5) == request.type().serialLetter()) {
                return true;
            }
            // Report the error on the serial field so the form can show it next to the input.
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("serial")
                    .addConstraintViolation();
            return false;
        }
    }
}
