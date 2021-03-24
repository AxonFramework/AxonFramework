package org.axonframework.axonserver.connector.util;

import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.serialization.SerializationException;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ErrorCodeDecider}
 * @author Milan Savic
 */
class ErrorCodeDeciderTest {

    @Test
    void queryExecutionErrorCodeFromNonTransientException() {
        ErrorCode errorCode = ErrorCodeDecider.getQueryExecutionErrorCode(new SerializationException("Fake exception"));
        assertEquals(ErrorCode.QUERY_EXECUTION_NON_TRANSIENT_ERROR, errorCode);
    }

    @Test
    void queryExecutionErrorCodeFromRuntimeException() {
        ErrorCode errorCode = ErrorCodeDecider.getQueryExecutionErrorCode(new RuntimeException("Fake exception"));
        assertEquals(ErrorCode.QUERY_EXECUTION_ERROR, errorCode);
    }

    @Test
    void commandExecutionErrorCodeFromNonTransientException() {
        ErrorCode errorCode = ErrorCodeDecider.getCommandExecutionErrorCode(new SerializationException("Fake exception"));
        assertEquals(ErrorCode.COMMAND_EXECUTION_NON_TRANSIENT_ERROR, errorCode);
    }

    @Test
    void commandExecutionErrorCodeFromRuntimeException() {
        ErrorCode errorCode = ErrorCodeDecider.getCommandExecutionErrorCode(new RuntimeException("Fake exception"));
        assertEquals(ErrorCode.COMMAND_EXECUTION_ERROR, errorCode);
    }
}