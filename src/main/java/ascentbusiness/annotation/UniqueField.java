package ascentbusiness.annotation;

import javax.validation.Constraint;
import javax.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field-level annotation to mark a field as unique and validate for duplicates.
 *
 * Can be used on fields directly:
 * @UniqueField(message = "This email already exists")
 * private String email;
 *
 * Or inside @CheckDuplicateEntity:
 * @CheckDuplicateEntity({
 *     @UniqueField(fieldName = "email", message = "Email already exists")
 * })
 */
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueFieldValidator.class)
public @interface UniqueField {

    /**
     * Name of the field to check for uniqueness (required when used inside @CheckDuplicateEntity)
     */
    String fieldName() default "";

    /**
     * Error message when duplicate is found
     */
    String message() default "This value already exists";

    /**
     * Validation groups
     */
    Class<?>[] groups() default {};

    /**
     * Payload for clients
     */
    Class<? extends Payload>[] payload() default {};

    /**
     * Whether to ignore the check if updating (when ID is present)
     * Default: true (will exclude current entity from duplicate check)
     */
    boolean excludeSelfOnUpdate() default true;

    /**
     * Only check for duplicates if this field equals the specified value
     * Example: onlyIfFieldEquals = "isEnabled"
     */
    String onlyIfFieldEquals() default "";

    /**
     * The value that the field should equal for duplicate checking
     * Example: fieldValue = "true"
     */
    String fieldValue() default "true";

    /**
     * Additional fields that must also match for a duplicate to be considered
     * Example: combinedWith = {"email"} means check firstName AND email together
     */
    String[] combinedWith() default {};
}
