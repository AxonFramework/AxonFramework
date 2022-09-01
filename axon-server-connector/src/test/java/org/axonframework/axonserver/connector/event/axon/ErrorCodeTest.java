/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.event.axon;

import io.axoniq.axonserver.grpc.ErrorMessage;
import org.axonframework.axonserver.connector.AxonServerException;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.command.AxonServerNonTransientRemoteCommandHandlingException;
import org.axonframework.axonserver.connector.query.AxonServerNonTransientRemoteQueryHandlingException;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.common.AxonException;
import org.axonframework.queryhandling.QueryExecutionException;
import org.axonframework.serialization.SerializationException;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Author: marc
 */
class ErrorCodeTest {

    @Test
    void convert4002FromCodeAndMessage() {
        ErrorCode errorCode = ErrorCode.getFromCode("AXONIQ-4002");
        AxonException exception = errorCode.convert(ErrorMessage.newBuilder().setMessage("myMessage").build(), () -> "myCustomObject");
        assertTrue(exception instanceof CommandExecutionException);
        assertEquals("myMessage", exception.getMessage());
        assertEquals("myCustomObject", ((CommandExecutionException) exception).getDetails().orElse("null"));
    }

    @Test
    void convertUnknownFromCodeAndMessage() {
        ErrorCode errorCode = ErrorCode.getFromCode("????????");
        AxonException exception = errorCode.convert(ErrorMessage.newBuilder().setMessage("myMessage").build());
        assertTrue(exception instanceof AxonServerException);
        assertEquals("myMessage", exception.getMessage());
    }

    @Test
    void convertWithoutSource() {
        RuntimeException exception = new RuntimeException("oops");
        AxonException axonException = ErrorCode.getFromCode("AXONIQ-4002").convert(exception);
        assertEquals(exception.getMessage(), axonException.getMessage());
    }

    @Test
    void convert4005FromCodeAndMessage() {
        ErrorCode errorCode = ErrorCode.getFromCode("AXONIQ-4005");
        AxonException exception = errorCode.convert(ErrorMessage.newBuilder().setMessage("myMessage").build(), () -> "myCustomObject");
        assertTrue(exception instanceof CommandExecutionException);
        assertTrue(exception.getCause() instanceof AxonServerNonTransientRemoteCommandHandlingException);
        assertEquals("myMessage", exception.getMessage());
        assertEquals("myCustomObject", ((CommandExecutionException) exception).getDetails().orElse("null"));
    }

    @Test
    void convert5003FromCodeAndMessage() {
        ErrorCode errorCode = ErrorCode.getFromCode("AXONIQ-5003");
        AxonException exception = errorCode.convert(ErrorMessage.newBuilder().setMessage("myMessage").build(), () -> "myCustomObject");
        assertTrue(exception instanceof QueryExecutionException);
        assertTrue(exception.getCause() instanceof AxonServerNonTransientRemoteQueryHandlingException);
        assertEquals("myMessage", exception.getMessage());
        assertEquals("myCustomObject", ((QueryExecutionException) exception).getDetails().orElse("null"));
    }

    @Test
    void queryExecutionErrorCodeFromNonTransientException() {
        ErrorCode errorCode = ErrorCode.getQueryExecutionErrorCode(new SerializationException("Fake exception"));
        assertEquals(ErrorCode.QUERY_EXECUTION_NON_TRANSIENT_ERROR, errorCode);
    }

    @Test
    void queryExecutionErrorCodeFromRuntimeException() {
        ErrorCode errorCode = ErrorCode.getQueryExecutionErrorCode(new RuntimeException("Fake exception"));
        assertEquals(ErrorCode.QUERY_EXECUTION_ERROR, errorCode);
    }

    @Test
    void commandExecutionErrorCodeFromNonTransientException() {
        ErrorCode errorCode = ErrorCode.getCommandExecutionErrorCode(new SerializationException("Fake exception"));
        assertEquals(ErrorCode.COMMAND_EXECUTION_NON_TRANSIENT_ERROR, errorCode);
    }

    @Test
    void commandExecutionErrorCodeFromRuntimeException() {
        ErrorCode errorCode = ErrorCode.getCommandExecutionErrorCode(new RuntimeException("Fake exception"));
        assertEquals(ErrorCode.COMMAND_EXECUTION_ERROR, errorCode);
    }

}
