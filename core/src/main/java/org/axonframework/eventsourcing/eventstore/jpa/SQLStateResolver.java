package org.axonframework.eventsourcing.eventstore.jpa;

import java.sql.SQLException;

/**
 * SQLStateResolver is an implementation of PersistenceExceptionResolver used to resolve sql state values to see if
 * it violates a unique key constraint.
 * <p/>
 * SQL state codes are standardized - the leading two characters identifying the category. Integrity constraint
 * violations are in category 23. Some database systems further specify these state codes, e.g. postgres uses 23505 for a
 * unique key violation.
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
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    public boolean isDuplicateKeyViolation(Exception exception) {
        boolean isDuplicateKey = false;
        SQLException sqlException = findSQLException(exception);
        if (sqlException != null && sqlException.getSQLState() != null) {
            if (sqlException.getSQLState().startsWith(checkCode)) {
                isDuplicateKey = true;
            }
        }
        return isDuplicateKey;
    }

    private SQLException findSQLException(Throwable exception) {
        SQLException sqlException = null;
        while (sqlException == null && exception != null) {
            if (exception instanceof SQLException) {
                sqlException = (SQLException) exception;
            } else {
                exception = exception.getCause();
            }
        }
        return sqlException;
    }
}
