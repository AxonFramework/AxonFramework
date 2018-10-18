/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.axonframework.axonserver.connector.command.AxonServerRemoteCommandHandlingException;
import org.axonframework.axonserver.connector.query.AxonServerQueryDispatchException;
import org.axonframework.axonserver.connector.query.AxonServerRemoteQueryHandlingException;
import org.axonframework.axonserver.connector.util.ExceptionSerializer;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.common.AxonException;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.messaging.EventPublicationFailedException;
import org.axonframework.modelling.command.ConcurrencyException;
import org.axonframework.queryhandling.NoHandlerForQueryException;
import org.axonframework.queryhandling.QueryExecutionException;

import java.util.function.BiFunction;

import static java.util.Arrays.stream;


/**
 * Converts an Axon Server Error to the relevant Axon framework exception.
 */
public enum ErrorCode {
    // Generic errors processing client request
    AUTHENTICATION_TOKEN_MISSING("AXONIQ-1000", AxonServerException::new),
    AUTHENTICATION_INVALID_TOKEN("AXONIQ-1001", AxonServerException::new),

    //Event publishing errors
    INVALID_EVENT_SEQUENCE("AXONIQ-2000", (code, error) -> new ConcurrencyException(error.getMessage(), new AxonServerException(code, error))),
    NO_EVENT_STORE_MASTER_AVAILABLE("AXONIQ-2100", (code, error) -> new EventPublicationFailedException(error.getMessage(), new AxonServerException(code, error))),
    EVENT_PAYLOAD_TOO_LARGE("AXONIQ-2001", (code, error) -> new EventPublicationFailedException(error.getMessage(), new AxonServerException(code, error))),

    //Communication errors
    CONNECTION_FAILED("AXONIQ-3001", AxonServerException::new),
    GRPC_MESSAGE_TOO_LARGE("AXONIQ-3002", AxonServerException::new),

    // Command errors
    NO_HANDLER_FOR_COMMAND("AXONIQ-4000", (code, error) -> new NoHandlerForCommandException(error.getMessage())),
    COMMAND_EXECUTION_ERROR("AXONIQ-4002", (code, error) -> new CommandExecutionException(error.getMessage(),  new AxonServerRemoteCommandHandlingException(code, error))),
    COMMAND_DISPATCH_ERROR("AXONIQ-4003", AxonServerCommandDispatchException::new),

    //Query errors
    NO_HANDLER_FOR_QUERY("AXONIQ-5000", (code,error) -> new NoHandlerForQueryException(error.getMessage())),
    QUERY_EXECUTION_ERROR("AXONIQ-5001", (code, error) -> new QueryExecutionException(
            error.getMessage(), new AxonServerRemoteQueryHandlingException(code, error))
    ),
    QUERY_DISPATCH_ERROR("AXONIQ-5002", AxonServerQueryDispatchException::new),

    // Internal errors
    DATAFILE_READ_ERROR( "AXONIQ-9000", (code, error) -> new EventStoreException(error.getMessage(), new AxonServerException(code, error))),
    INDEX_READ_ERROR( "AXONIQ-9001", (code, error) -> new EventStoreException(error.getMessage(), new AxonServerException(code, error))),
    DATAFILE_WRITE_ERROR( "AXONIQ-9100", (code, error) -> new EventStoreException(error.getMessage(), new AxonServerException(code, error))),
    INDEX_WRITE_ERROR( "AXONIQ-9101", (code, error) -> new EventStoreException(error.getMessage(), new AxonServerException(code, error))),
    DIRECTORY_CREATION_FAILED("AXONIQ-9102", (code, error) -> new EventStoreException(error.getMessage(), new AxonServerException(code, error))),
    VALIDATION_FAILED( "AXONIQ-9200", (code, error) -> new EventStoreException(error.getMessage(), new AxonServerException(code, error))),
    TRANSACTION_ROLLED_BACK( "AXONIQ-9900", (code, error) -> new EventStoreException(error.getMessage(), new AxonServerException(code, error))),

    //Default
    OTHER("AXONIQ-0001", AxonServerException::new);

    private final String errorCode;
    private final BiFunction<String, ErrorMessage, ? extends AxonException> exceptionBuilder;

    /**
     * Initializes the ErrorCode using the given {@code code} and {@code exceptionBuilder}
     *
     * @param errorCode the code of the error
     * @param exceptionBuilder the function to build the relevant Axon Framework exception
     */
    ErrorCode(String errorCode, BiFunction<String, ErrorMessage, ? extends AxonException> exceptionBuilder) {
        this.errorCode = errorCode;
        this.exceptionBuilder = exceptionBuilder;
    }

    public static ErrorCode getFromCode(String code){
        return stream(values()).filter(value -> value.errorCode.equals(code)).findFirst().orElse(OTHER);
    }

    public String errorCode() {
        return errorCode;
    }

    /**
     * Converts the {@code errorMessage} to the relevant AxonException
     *
     * @param errorMessage the descriptor of the error
     * @return the Axon Framework exception
     */
    public AxonException convert(ErrorMessage errorMessage){
        return exceptionBuilder.apply(errorCode, errorMessage);
    }

    /**
     * Converts the {@code throwable} to the relevant AxonException
     *
     * @param throwable the descriptor of the error
     * @return the Axon Framework exception
     */
    public AxonException convert(Throwable throwable){
        return convert(null, throwable);
    }

    /**
     * Converts the {@code source} and the {@code throwable} to the relevant AxonException
     *
     * @param source The location that originally reported the error
     * @param throwable the descriptor of the error
     * @return the Axon Framework exception
     */
    public AxonException convert(String source, Throwable throwable){
        return convert(ExceptionSerializer.serialize(source, throwable));
    }
}
