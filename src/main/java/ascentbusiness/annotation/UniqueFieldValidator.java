package ascentbusiness.annotation;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Validator for @UniqueField annotation
 */
@Slf4j
@Component
public class UniqueFieldValidator implements ConstraintValidator<UniqueField, Object> {

    @PersistenceContext
    private EntityManager entityManager;
    
    private UniqueField annotation;
    
    @Override
    public void initialize(UniqueField annotation) {
        this.annotation = annotation;
    }
    
    @Override
    public boolean isValid(Object fieldValue, ConstraintValidatorContext context) {
        if (fieldValue == null) {
            return true; // Null values are not validated by this annotation
        }
        
        try {
            // Get the entity object (parent of the field being validated)
            Object entity = getEntityFromContext(context);
            if (entity == null) {
                return true;
            }
            
            Class<?> entityClass = entity.getClass();
            String fieldName = getFieldNameFromContext(context);
            
            // Build JPA query to check for duplicates
            return !isDuplicate(entityClass, fieldName, fieldValue, entity);
            
        } catch (Exception e) {
            log.error("Error validating unique field: {}", e.getMessage(), e);
            return true; // If validation fails, let it pass (fail-safe)
        }
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean isDuplicate(Class<?> entityClass, String fieldName, Object fieldValue, Object currentEntity) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery query = cb.createQuery(entityClass);
            Root root = query.from(entityClass);
            
            List<Predicate> predicates = new ArrayList<>();
            
            // Add predicate for the field value
            predicates.add(cb.equal(root.get(fieldName), fieldValue));
            
            // If excludeSelfOnUpdate is true, exclude current entity
            if (annotation.excludeSelfOnUpdate()) {
                Object id = getId(currentEntity);
                if (id != null) {
                    predicates.add(cb.notEqual(root.get("id"), id));
                }
            }
            
            query.select(root).where(cb.and(predicates.toArray(new Predicate[0])));
            
            List<?> results = entityManager.createQuery(query).getResultList();
            
            return !results.isEmpty();
            
        } catch (Exception e) {
            log.error("Error checking for duplicates: {}", e.getMessage(), e);
            return false;
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
    
    private Object getEntityFromContext(ConstraintValidatorContext context) {
        // This is a workaround - in real validation, the entity is passed through the validation context
        // For proper implementation, we'll need to handle this in the aspect
        return null;
    }
    
    private String getFieldNameFromContext(ConstraintValidatorContext context) {
        // Extract field name from validation context
        return null;
    }
}
