/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.axonserver.connector;

import io.axoniq.axonserver.grpc.ErrorMessage;
import org.axonframework.axonserver.connector.command.AxonServerCommandDispatchException;
import org.axonframework.axonserver.connector.command.AxonServerNonTransientRemoteCommandHandlingException;
import org.axonframework.axonserver.connector.command.AxonServerRemoteCommandHandlingException;
import org.axonframework.axonserver.connector.query.AxonServerNonTransientRemoteQueryHandlingException;
import org.axonframework.axonserver.connector.query.AxonServerQueryDispatchException;
import org.axonframework.axonserver.connector.query.AxonServerRemoteQueryHandlingException;
import org.axonframework.axonserver.connector.util.ExceptionSerializer;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.common.AxonException;
import org.axonframework.common.ExceptionUtils;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.messaging.EventPublicationFailedException;
import org.axonframework.messaging.HandlerExecutionException;
import org.axonframework.modelling.command.ConcurrencyException;
import org.axonframework.queryhandling.NoHandlerForQueryException;
import org.axonframework.queryhandling.QueryExecutionException;

import java.util.function.Supplier;

import static java.util.Arrays.stream;

/**
 * Converts an Axon Server Error to the relevant Axon framework exception.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public enum ErrorCode {

    // Generic errors processing client request
    AUTHENTICATION_TOKEN_MISSING("AXONIQ-1000", (code, error, details) -> new AxonServerException(code, error)),
    AUTHENTICATION_INVALID_TOKEN("AXONIQ-1001", (code, error, details) -> new AxonServerException(code, error)),
    UNSUPPORTED_INSTRUCTION("AXONIQ-1002", (code, error, details) -> new AxonServerException(code, error)),
    INSTRUCTION_ACK_ERROR("AXONIQ-1003", (code, error, details) -> new AxonServerException(code, error)),
    INSTRUCTION_EXECUTION_ERROR("AXONIQ-1004", (code, error, details) -> new AxonServerException(code, error)),

    //Event publishing errors
    INVALID_EVENT_SEQUENCE(
            "AXONIQ-2000",
            (code, error, details) -> new ConcurrencyException(error.getMessage(), new AxonServerException(code, error))
    ),
    NO_EVENT_STORE_MASTER_AVAILABLE(
            "AXONIQ-2100",
            (code, error, details) -> new EventPublicationFailedException(
                    error.getMessage(),
                    new AxonServerException(code, error)
            )
    ),
    EVENT_PAYLOAD_TOO_LARGE(
            "AXONIQ-2001",
            (code, error, details) -> new EventPublicationFailedException(
                    error.getMessage(),
                    new AxonServerException(code, error)
            )
    ),

    //Communication errors
    CONNECTION_FAILED("AXONIQ-3001", (code, error, details) -> new AxonServerException(code, error)),
    GRPC_MESSAGE_TOO_LARGE("AXONIQ-3002", (code, error, details) -> new AxonServerException(code, error)),

    // Command errors
    NO_HANDLER_FOR_COMMAND(
            "AXONIQ-4000",
            (code, error, details) -> new NoHandlerForCommandException(error.getMessage())
    ),
    COMMAND_EXECUTION_ERROR(
            "AXONIQ-4002",
            (code, error, details) -> new CommandExecutionException(
                    error.getMessage(),
                    new AxonServerRemoteCommandHandlingException(code, error),
                    details.get()
            )
    ),
    COMMAND_DISPATCH_ERROR(
            "AXONIQ-4003",
            (code, error, details) -> new AxonServerCommandDispatchException(code, error)),
    CONCURRENCY_EXCEPTION(
            "AXONIQ-4004",
            (code, error, details) -> new ConcurrencyException(
                    error.getMessage(),
                    new AxonServerRemoteCommandHandlingException(code, error)
            )
    ),
    COMMAND_EXECUTION_NON_TRANSIENT_ERROR(
            "AXONIQ-4005",
            (code, error, details) -> new CommandExecutionException(
                    error.getMessage(),
                    new AxonServerNonTransientRemoteCommandHandlingException(code, error),
                    details.get()
            )
    ),

    //Query errors
    NO_HANDLER_FOR_QUERY("AXONIQ-5000", (code, error, details) -> new NoHandlerForQueryException(error.getMessage())),
    QUERY_EXECUTION_ERROR(
            "AXONIQ-5001",
            (code, error, details) -> new QueryExecutionException(
                    error.getMessage(),
                    new AxonServerRemoteQueryHandlingException(code, error),
                    details.get()
            )
    ),
    QUERY_DISPATCH_ERROR("AXONIQ-5002", (code, error, details) -> new AxonServerQueryDispatchException(code, error)),
    QUERY_EXECUTION_NON_TRANSIENT_ERROR(
            "AXONIQ-5003",
            (code, error, details) -> new QueryExecutionException(
                    error.getMessage(),
                    new AxonServerNonTransientRemoteQueryHandlingException(code, error),
                    details.get()
            )
    ),

    // Internal errors
    DATAFILE_READ_ERROR(
            "AXONIQ-9000",
            (code, error, details) -> new EventStoreException(error.getMessage(), new AxonServerException(code, error))
    ),
    INDEX_READ_ERROR(
            "AXONIQ-9001",
            (code, error, details) -> new EventStoreException(error.getMessage(), new AxonServerException(code, error))
    ),
    DATAFILE_WRITE_ERROR(
            "AXONIQ-9100",
            (code, error, details) -> new EventStoreException(error.getMessage(), new AxonServerException(code, error))
    ),
    INDEX_WRITE_ERROR(
            "AXONIQ-9101",
            (code, error, details) -> new EventStoreException(error.getMessage(), new AxonServerException(code, error))
    ),
    DIRECTORY_CREATION_FAILED(
            "AXONIQ-9102",
            (code, error, details) -> new EventStoreException(error.getMessage(), new AxonServerException(code, error))
    ),
    VALIDATION_FAILED(
            "AXONIQ-9200",
            (code, error, details) -> new EventStoreException(error.getMessage(), new AxonServerException(code, error))
    ),
    TRANSACTION_ROLLED_BACK(
            "AXONIQ-9900",
            (code, error, details) -> new EventStoreException(error.getMessage(), new AxonServerException(code, error))
    ),

    //Default
    OTHER("AXONIQ-0001", (code, error, details) -> new AxonServerException(code, error));

    private final String errorCode;
    private final ExceptionBuilder exceptionBuilder;

    public static ErrorCode getFromCode(String code) {
        return stream(values()).filter(value -> value.errorCode.equals(code)).findFirst().orElse(OTHER);
    }

    /**
     * Initializes the ErrorCode using the given {@code code} and {@code exceptionBuilder}
     *
     * @param errorCode        the code of the error
     * @param exceptionBuilder the function to build the relevant Axon Framework exception
     */
    ErrorCode(String errorCode, ExceptionBuilder exceptionBuilder) {
        this.errorCode = errorCode;
        this.exceptionBuilder = exceptionBuilder;
    }

    public String errorCode() {
        return errorCode;
    }

    /**
     * Converts the {@code errorMessage} to the relevant AxonException
     *
     * @param errorMessage the descriptor of the error
     * @param details      a supplier of (optional) application-specific details to be included in Exception, when
     *                     appropriate
     * @return the Axon Framework exception
     */
    public AxonException convert(ErrorMessage errorMessage, Supplier<Object> details) {
        if (details == null) {
            // safeguard to prevent NullPointerException
            return convert(errorMessage);
        }
        return exceptionBuilder.buildException(errorCode, errorMessage, details);
    }

    /**
     * Converts the {@code errorMessage} to the relevant AxonException
     *
     * @param errorMessage the descriptor of the error
     * @return the Axon Framework exception
     */
    public AxonException convert(ErrorMessage errorMessage) {
        return exceptionBuilder.buildException(errorCode, errorMessage, () -> null);
    }

    /**
     * Converts the {@code throwable} to the relevant AxonException
     *
     * @param throwable the descriptor of the error
     * @return the Axon Framework exception
     */
    public AxonException convert(Throwable throwable) {
        return convert("", throwable);
    }

    /**
     * Converts the {@code source} and the {@code throwable} to the relevant AxonException
     *
     * @param source    The location that originally reported the error
     * @param throwable the descriptor of the error
     * @return the Axon Framework exception
     */
    public AxonException convert(String source, Throwable throwable) {
        return convert(ExceptionSerializer.serialize(source, throwable),
                       () -> HandlerExecutionException.resolveDetails(throwable).orElse(null));
    }

    /**
     * Returns an Query Execution ErrorCode variation based on the transiency of the given {@link Throwable}
     * @param throwable The {@link Throwable} to inspect for transiency
     * @return {@link ErrorCode} variation
     */
    public static ErrorCode getQueryExecutionErrorCode(Throwable throwable) {
        if (ExceptionUtils.isExplicitlyNonTransient(throwable)) {
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
        if (ExceptionUtils.isExplicitlyNonTransient(throwable)) {
            return ErrorCode.COMMAND_EXECUTION_NON_TRANSIENT_ERROR;
        } else {
            return ErrorCode.COMMAND_EXECUTION_ERROR;
        }
    }

    /**
     * Functional interface towards building an {@link AxonException} based on an {@code errorCode}, {@link
     * ErrorMessage} and the {@link Supplier} of an {@link Object}. This provides the option for users to specify more
     * thorough exception messages when it is serialized from one Axon client to another.
     */
    @FunctionalInterface
    private interface ExceptionBuilder {

        /**
         * Build an {@link AxonException} containing the {@code errorCode}, {@code errorMessage} and a {@link Supplier}
         * for additional client specific details on the occurrence of the exception.
         *
         * @param errorCode       a {@link String} defining the error code of the exception
         * @param errorMessage    an {@link ErrorMessage}
         * @param detailsSupplier a details {@link Supplier} used to provide additional context to the exception
         * @return an {@link AxonException}
         */
        AxonException buildException(String errorCode, ErrorMessage errorMessage, Supplier<Object> detailsSupplier);
    }
}
