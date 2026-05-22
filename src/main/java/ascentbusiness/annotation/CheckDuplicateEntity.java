package ascentbusiness.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Entity-level annotation to automatically check for duplicates based on field configurations.
 *
 * Usage on Entity class:
 * @Entity
 * @CheckDuplicateEntity({
 *     @UniqueField(fieldName = "email", message = "Email already exists"),
 *     @UniqueField(fieldName = "firstName", message = "Name exists", onlyIfFieldEquals = "isEnabled", fieldValue = "true")
 * })
 * public class Customer {
 *     private String email;
 *     private String firstName;
 * }
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckDuplicateEntity {

    /**
     * Array of unique field configurations
     */
    UniqueField[] value() default {};

    /**
     * Whether to enable duplicate checking for this entity
     */
    boolean enabled() default true;
}
