package ascentbusiness.aspect;

import ascentbusiness.annotation.CheckDuplicate;
import ascentbusiness.exception.DuplicateEntryException;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Aspect to handle duplicate checking for entities (METHOD-LEVEL)
 * DEPRECATED: Use EntityDuplicateCheckAspect instead (ENTITY-LEVEL)
 */
@Aspect
//@Component  // Disabled - using EntityDuplicateCheckAspect instead
public class DuplicateCheckAspect {
    
    private static final Logger log = LoggerFactory.getLogger(DuplicateCheckAspect.class);
    
    private final EntityManager entityManager;
    
    public DuplicateCheckAspect(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
    
    @Before("@annotation(ascentbusiness.annotation.CheckDuplicate)")
    public void checkDuplicate(JoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        CheckDuplicate annotation = method.getAnnotation(CheckDuplicate.class);
        
        // Get the parameter to validate
        Object[] args = joinPoint.getArgs();
        if (args.length <= annotation.paramIndex()) {
            log.warn("Invalid paramIndex {} for method {}", annotation.paramIndex(), method.getName());
            return;
        }
        
        Object dto = args[annotation.paramIndex()];
        if (dto == null) {
            return;
        }
        
        // Check for duplicates
        checkForDuplicates(dto, annotation);
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void checkForDuplicates(Object dto, CheckDuplicate annotation) throws Exception {
        Class entityClass = annotation.entity();
        String[] fieldsToCheck = annotation.fields();

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery query = cb.createQuery(entityClass);
        Root root = query.from(entityClass);

        List<Predicate> predicates = new ArrayList<>();

        // Build predicates for each field to check
        for (String fieldName : fieldsToCheck) {
            Object fieldValue = getFieldValue(dto, fieldName);

            if (fieldValue != null) {
                predicates.add(cb.equal(root.get(fieldName), fieldValue));
            }
        }

        if (predicates.isEmpty()) {
            return; // No fields to check
        }

        // If updating, exclude the current entity
        if (annotation.excludeSelfOnUpdate()) {
            Object id = getFieldValue(dto, "id");
            if (id != null) {
                predicates.add(cb.notEqual(root.get("id"), id));
            }
        }

        query.select(root).where(cb.and(predicates.toArray(new Predicate[0])));

        List<?> results = entityManager.createQuery(query).getResultList();

        if (!results.isEmpty()) {
            String message = annotation.message();
            if (message.isEmpty()) {
                message = buildDefaultMessage(fieldsToCheck, dto);
            }

            log.warn("Duplicate entry detected: {}", message);
            throw new DuplicateEntryException(message);
        }
    }
    
    private Object getFieldValue(Object obj, String fieldName) throws Exception {
        try {
            // Try to get value using getter method
            String getterName = "get" + capitalize(fieldName);
            Method getter = obj.getClass().getMethod(getterName);
            return getter.invoke(obj);
        } catch (NoSuchMethodException e) {
            // Fallback to direct field access
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        }
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    private String buildDefaultMessage(String[] fields, Object dto) throws Exception {
        StringBuilder sb = new StringBuilder("Duplicate entry found for ");
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(", ");
            Object value = getFieldValue(dto, fields[i]);
            sb.append(fields[i]).append("='").append(value).append("'");
        }
        return sb.toString();
    }
}
