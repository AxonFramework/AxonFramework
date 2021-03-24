package org.axonframework.axonserver.connector.util;

import org.axonframework.axonserver.connector.ErrorCode;

/**
 * Utility class used to pick correct {@link ErrorCode} from a {@link Throwable}
 *
 * @author Stefan Andjelkovic
 * @since 4.5
 */
public class ErrorCodeDecider {

    private ErrorCodeDecider() {
        // Utility class
    }

    /**
     * Returns an Query Execution ErrorCode variation based on the transiency of the given {@link Throwable}
     * @param throwable The {@link Throwable} to inspect for transiency
     * @return {@link ErrorCode} variation
     */
    public static ErrorCode getQueryExecutionErrorCode(Throwable throwable) {
        if (ExceptionSerializer.isExplicitlyNonTransient(throwable)) {
            return ErrorCode.QUERY_EXECUTION_NON_TRANSIENT_ERROR;
        } else {
            return ErrorCode.QUERY_EXECUTION_ERROR;
        }
    }

    /**
     * Returns an Command Execution ErrorCode variation based on the transiency of the given {@link Throwable}
     * @param throwable The {@link Throwable} to inspect for transiency
     * @return {@link ErrorCode} variation
     */
    public static ErrorCode getCommandExecutionErrorCode(Throwable throwable) {
        if (ExceptionSerializer.isExplicitlyNonTransient(throwable)) {
            return ErrorCode.COMMAND_EXECUTION_NON_TRANSIENT_ERROR;
        } else {
            return ErrorCode.COMMAND_EXECUTION_ERROR;
        }
    }
}
