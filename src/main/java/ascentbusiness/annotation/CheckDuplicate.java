package ascentbusiness.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation to check for duplicate entries before saving/updating entities.
 * 
 * Usage:
 * @CheckDuplicate(entity = Customer.class, fields = {"email"}, message = "Customer with this email already exists")
 * public CustomerDTO createCustomer(CustomerDTO customerDTO) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckDuplicate {
    
    /**
     * The entity class to check for duplicates
     */
    Class<?> entity();
    
    /**
     * The fields to check for duplicates (e.g., "email", "sku")
     * Multiple fields can be specified for composite uniqueness
     */
    String[] fields();
    
    /**
     * Custom error message to display when duplicate is found
     * Default: "Duplicate entry found for {fields}"
     */
    String message() default "";
    
    /**
     * The index of the method parameter that contains the DTO/Entity to validate
     * Default: 0 (first parameter)
     */
    int paramIndex() default 0;
    
    /**
     * Whether to ignore the check if updating (when ID is present)
     * Default: true (will exclude current entity from duplicate check)
     */
    boolean excludeSelfOnUpdate() default true;
}
