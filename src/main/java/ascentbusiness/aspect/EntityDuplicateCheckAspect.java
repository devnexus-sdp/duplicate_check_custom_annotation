package ascentbusiness.aspect;

import ascentbusiness.annotation.CheckDuplicateEntity;
import ascentbusiness.annotation.UniqueField;
import ascentbusiness.exception.DuplicateEntryException;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Aspect to check for duplicates at entity level using @CheckDuplicateEntity and @UniqueField annotations
 */
@Aspect
@Component
public class EntityDuplicateCheckAspect {
    
    private static final Logger log = LoggerFactory.getLogger(EntityDuplicateCheckAspect.class);
    
    private final EntityManager entityManager;
    
    public EntityDuplicateCheckAspect(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
    
    /**
     * Intercept all save methods in repositories and check for entity-level duplicate annotations
     */
    @Before("execution(* org.springframework.data.repository.CrudRepository+.save(..)) || " +
            "execution(* org.springframework.data.jpa.repository.JpaRepository+.save(..))")
    public void checkEntityDuplicates(JoinPoint joinPoint) {
        log.info("=== EntityDuplicateCheckAspect triggered ===");

        Object[] args = joinPoint.getArgs();
        if (args.length == 0) {
            log.debug("No arguments found in save method");
            return;
        }

        Object entity = args[0];
        if (entity == null) {
            log.debug("Entity is null");
            return;
        }

        // Get the actual class (handle Hibernate proxies)
        Class<?> entityClass = entity.getClass();

        // If it's a Hibernate proxy, get the real class
        String className = entityClass.getName();
        if (className.contains("_$$_")) {
            // Hibernate proxy detected
            entityClass = entityClass.getSuperclass();
            log.info("Hibernate proxy detected, using superclass: {}", entityClass.getName());
        }

        log.info("Checking entity class: {}", entityClass.getName());

        // Check if entity has @CheckDuplicateEntity annotation
        if (entityClass.isAnnotationPresent(CheckDuplicateEntity.class)) {
            log.info("@CheckDuplicateEntity annotation found on {}", entityClass.getName());
            CheckDuplicateEntity annotation = entityClass.getAnnotation(CheckDuplicateEntity.class);

            if (annotation.enabled()) {
                log.info("Duplicate checking is enabled, proceeding with validation");
                checkUniqueFields(entity, entityClass);
            } else {
                log.info("Duplicate checking is disabled for this entity");
            }
        } else {
            log.debug("No @CheckDuplicateEntity annotation found on {}", entityClass.getName());
        }
    }
    
    private void checkUniqueFields(Object entity, Class<?> entityClass) {
        log.info("=== checkUniqueFields called for {} ===", entityClass.getName());

        CheckDuplicateEntity entityAnnotation = entityClass.getAnnotation(CheckDuplicateEntity.class);

        // Check if unique fields are defined in @CheckDuplicateEntity annotation
        if (entityAnnotation.value() != null && entityAnnotation.value().length > 0) {
            log.info("Found {} unique field configurations", entityAnnotation.value().length);

            // Use entity-level configuration
            for (UniqueField uniqueField : entityAnnotation.value()) {
                String fieldName = uniqueField.fieldName();
                log.info("Processing unique field: {}", fieldName);

                if (fieldName != null && !fieldName.isEmpty()) {
                    try {
                        // Support nested fields like "mdmVitalRecord.id"
                        if (fieldName.contains(".")) {
                            log.info("Detected nested field path: {}", fieldName);
                            // For nested fields, pass null as Field and handle in checkFieldUniqueness
                            checkFieldUniquenessNested(entity, entityClass, fieldName, uniqueField);
                        } else {
                            log.info("Simple field: {}", fieldName);
                            Field field = entityClass.getDeclaredField(fieldName);
                            checkFieldUniqueness(entity, entityClass, field, uniqueField);
                        }
                    } catch (NoSuchFieldException e) {
                        log.error("Field '{}' not found in entity {}", fieldName, entityClass.getName());
                    }
                }
            }
        } else {
            log.info("No entity-level unique fields configured, checking field-level annotations");

            // Fallback to field-level annotations
            Field[] fields = entityClass.getDeclaredFields();

            for (Field field : fields) {
                if (field.isAnnotationPresent(UniqueField.class)) {
                    UniqueField uniqueAnnotation = field.getAnnotation(UniqueField.class);
                    checkFieldUniqueness(entity, entityClass, field, uniqueAnnotation);
                }
            }
        }
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void checkFieldUniqueness(Object entity, Class<?> entityClass, Field field, UniqueField annotation) {
        try {
            field.setAccessible(true);
            Object fieldValue = field.get(entity);

            if (fieldValue == null) {
                return; // Skip null values
            }

            // Check if we should skip based on conditional field (e.g., isEnabled = false for deletion)
            if (!annotation.onlyIfFieldEquals().isEmpty()) {
                String conditionalField = annotation.onlyIfFieldEquals();
                String conditionalValue = annotation.fieldValue();

                Object currentConditionalValue = getFieldValue(entity, conditionalField);
                Object expectedValue = convertValue(conditionalValue);

                // If the conditional field doesn't match the expected value, skip the check
                if (currentConditionalValue == null || !currentConditionalValue.equals(expectedValue)) {
                    log.debug("Skipping duplicate check: {} is not {}", conditionalField, conditionalValue);
                    return;
                }
            }

            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery query = cb.createQuery(entityClass);
            Root root = query.from(entityClass);

            List<Predicate> predicates = new ArrayList<>();

            // Add predicate for the field value
            predicates.add(cb.equal(root.get(field.getName()), fieldValue));

            // Add combined fields check (composite uniqueness)
            if (annotation.combinedWith() != null && annotation.combinedWith().length > 0) {
                for (String combinedFieldName : annotation.combinedWith()) {
                    try {
                        Object combinedValue = getFieldValue(entity, combinedFieldName);

                        if (combinedValue != null) {
                            // Support nested fields like "brp.id"
                            if (combinedFieldName.contains(".")) {
                                String[] parts = combinedFieldName.split("\\.");
                                predicates.add(cb.equal(root.get(parts[0]).get(parts[1]), combinedValue));
                            } else {
                                predicates.add(cb.equal(root.get(combinedFieldName), combinedValue));
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error getting combined field '{}': {}", combinedFieldName, e.getMessage());
                    }
                }
            }

            // Add conditional predicate to query (check only records with the same condition)
            if (!annotation.onlyIfFieldEquals().isEmpty()) {
                String conditionalField = annotation.onlyIfFieldEquals();
                String conditionalValue = annotation.fieldValue();
                Object convertedValue = convertValue(conditionalValue);
                predicates.add(cb.equal(root.get(conditionalField), convertedValue));
            }

            // If excludeSelfOnUpdate is true, exclude current entity
            if (annotation.excludeSelfOnUpdate()) {
                Object id = getId(entity);
                if (id != null) {
                    predicates.add(cb.notEqual(root.get("id"), id));
                }
            }

            query.select(root).where(cb.and(predicates.toArray(new Predicate[0])));

            List<?> results = entityManager.createQuery(query).getResultList();

            if (!results.isEmpty()) {
                String message = annotation.message();
                if (message.equals("This value already exists")) {
                    message = String.format("Duplicate value '%s' found for field '%s'", fieldValue, field.getName());
                }

                log.warn("Duplicate entry detected: {}", message);
                throw new DuplicateEntryException(field.getName(), fieldValue, message);
            }

        } catch (DuplicateEntryException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error checking field uniqueness", e);
        }
    }

    private Object convertValue(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.valueOf(value);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return value; // Return as string
        }
    }
    
    /**
     * Check uniqueness for nested field paths (e.g., "mdmVitalRecord.id")
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void checkFieldUniquenessNested(Object entity, Class<?> entityClass, String fieldPath, UniqueField annotation) {
        try {
            log.info("=== Starting duplicate check for nested field: {} ===", fieldPath);
            log.info("Entity class: {}", entityClass.getName());
            log.info("Entity ID: {}", getId(entity));

            // Get the value of the nested field
            Object fieldValue = getFieldValue(entity, fieldPath);
            log.info("Field value for '{}': {}", fieldPath, fieldValue);

            if (fieldValue == null) {
                log.warn("Main field '{}' is null - cannot check for duplicates without a value", fieldPath);
                return; // Skip null values
            }

            // Check if we should skip based on conditional field
            if (!annotation.onlyIfFieldEquals().isEmpty()) {
                String conditionalField = annotation.onlyIfFieldEquals();
                String conditionalValue = annotation.fieldValue();

                Object currentConditionalValue = getFieldValue(entity, conditionalField);
                Object expectedValue = convertValue(conditionalValue);

                log.info("Conditional check: field='{}', current='{}', expected='{}'",
                    conditionalField, currentConditionalValue, expectedValue);

                if (currentConditionalValue == null || !currentConditionalValue.equals(expectedValue)) {
                    log.info("Skipping duplicate check: {} is {} (expected {})",
                        conditionalField, currentConditionalValue, conditionalValue);
                    return;
                }
            }

            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery query = cb.createQuery(entityClass);
            Root root = query.from(entityClass);

            List<Predicate> predicates = new ArrayList<>();

            // Add predicate for the nested field value
            String[] parts = fieldPath.split("\\.");
            if (parts.length == 2) {
                predicates.add(cb.equal(root.get(parts[0]).get(parts[1]), fieldValue));
                log.info("Added main predicate: {}.{} = {}", parts[0], parts[1], fieldValue);
            } else {
                log.error("Nested field path '{}' is too deep. Only one level supported (e.g., 'object.id')", fieldPath);
                return;
            }

            // Add combined fields check
            if (annotation.combinedWith() != null && annotation.combinedWith().length > 0) {
                log.info("Processing {} combined fields", annotation.combinedWith().length);

                for (String combinedFieldName : annotation.combinedWith()) {
                    try {
                        Object combinedValue = getFieldValue(entity, combinedFieldName);
                        log.info("Combined field '{}' has value: {}", combinedFieldName, combinedValue);

                        // Skip if combined value is null - we can't check uniqueness on null
                        if (combinedValue == null) {
                            log.warn("Skipping combined field '{}' because value is null", combinedFieldName);
                            continue;
                        }

                        // IMPORTANT: If combined value is null, still add null check predicate
                        if (combinedFieldName.contains(".")) {
                            String[] combinedParts = combinedFieldName.split("\\.");
                            predicates.add(cb.equal(root.get(combinedParts[0]).get(combinedParts[1]), combinedValue));
                            log.info("Added nested combined predicate: {}.{} = {}", combinedParts[0], combinedParts[1], combinedValue);
                        } else {
                            predicates.add(cb.equal(root.get(combinedFieldName), combinedValue));
                            log.info("Added simple combined predicate: {} = {}", combinedFieldName, combinedValue);
                        }
                    } catch (Exception e) {
                        log.error("Error getting combined field '{}': {}", combinedFieldName, e.getMessage(), e);
                    }
                }
            }

            // Add conditional predicate to query
            if (!annotation.onlyIfFieldEquals().isEmpty()) {
                String conditionalField = annotation.onlyIfFieldEquals();
                String conditionalValue = annotation.fieldValue();
                Object convertedValue = convertValue(conditionalValue);
                predicates.add(cb.equal(root.get(conditionalField), convertedValue));
            }

            // Exclude self on update
            if (annotation.excludeSelfOnUpdate()) {
                Object id = getId(entity);
                if (id != null) {
                    predicates.add(cb.notEqual(root.get("id"), id));
                }
            }

            query.select(root).where(cb.and(predicates.toArray(new Predicate[0])));

            log.info("Total predicates: {}", predicates.size());
            log.info("Executing duplicate check query...");

            List<?> results = entityManager.createQuery(query).getResultList();

            log.info("Query returned {} results", results.size());

            if (!results.isEmpty()) {
                String message = annotation.message();
                if (message.equals("This value already exists")) {
                    message = String.format("Duplicate value '%s' found for field '%s'", fieldValue, fieldPath);
                }

                log.warn("DUPLICATE ENTRY DETECTED: {}", message);
                throw new DuplicateEntryException(fieldPath, fieldValue, message);
            } else {
                log.info("No duplicates found - record is unique");
            }

        } catch (DuplicateEntryException e) {
            throw e;
        } catch (Exception e) {
            log.error("ERROR checking field uniqueness for nested field '{}'", fieldPath, e);
        }
    }

    private Object getId(Object entity) {
        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            return idField.get(entity);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get field value supporting nested fields (e.g., "brp.id")
     */
    private Object getFieldValue(Object entity, String fieldPath) throws Exception {
        if (fieldPath.contains(".")) {
            // Handle nested fields like "brp.id"
            String[] parts = fieldPath.split("\\.");
            Object currentObject = entity;

            for (String part : parts) {
                if (currentObject == null) {
                    return null;
                }
                Field field = currentObject.getClass().getDeclaredField(part);
                field.setAccessible(true);
                currentObject = field.get(currentObject);
            }

            return currentObject;
        } else {
            // Simple field
            Field field = entity.getClass().getDeclaredField(fieldPath);
            field.setAccessible(true);
            return field.get(entity);
        }
    }
}
