package ascentbusiness.exception;

/**
 * Exception thrown when a duplicate entry is detected
 */
public class DuplicateEntryException extends RuntimeException {
    
    private final String field;
    private final Object value;
    
    public DuplicateEntryException(String message) {
        super(message);
        this.field = null;
        this.value = null;
    }
    
    public DuplicateEntryException(String field, Object value) {
        super(String.format("Duplicate entry found for field '%s' with value '%s'", field, value));
        this.field = field;
        this.value = value;
    }
    
    public DuplicateEntryException(String field, Object value, String customMessage) {
        super(customMessage);
        this.field = field;
        this.value = value;
    }
    
    public String getField() {
        return field;
    }
    
    public Object getValue() {
        return value;
    }
}
