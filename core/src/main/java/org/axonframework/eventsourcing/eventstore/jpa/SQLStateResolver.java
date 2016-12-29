package org.axonframework.eventsourcing.eventstore.jpa;

import java.sql.SQLException;

import static org.axonframework.common.ExceptionUtils.findException;

/**
 * SQLStateResolver is an implementation of PersistenceExceptionResolver used to resolve sql state values to see if it
 * violates a unique key constraint.
 * <p/>
 * SQL state codes are standardized - the leading two characters identifying the category. Integrity constraint
 * violations are in category 23. Some database systems further specify these state codes, e.g. postgres uses 23505 for
 * a unique key violation.
 *
 * @author Jochen Munz
 */
public class SQLStateResolver implements org.axonframework.common.jdbc.PersistenceExceptionResolver {

    private static final String INTEGRITY_CONSTRAINT_VIOLATION_CODE = "23";
    private String checkCode = null;

    /**
     * Constructor that uses the standard SQL state category for the check.
     */
    public SQLStateResolver() {
        checkCode = INTEGRITY_CONSTRAINT_VIOLATION_CODE;
    }

    /**
     * Constructor that can be used to supply a specific SQL state code for the check.
     * <p/>
     * The check is done using startsWith(), supplying a substring of the state code is ok.
     *
     * @param checkState The state string that is used in the check.
     */
    public SQLStateResolver(String checkState) {
        this.checkCode = checkState;
    }

    @Override
    public boolean isDuplicateKeyViolation(Exception exception) {
        return findException(exception, SQLException.class).filter(e -> e.getSQLState() != null)
                .map(e -> e.getSQLState().startsWith(checkCode)).orElse(false);
    }

}
