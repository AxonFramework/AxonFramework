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
import org.axonframework.axonserver.connector.command.RemoteCommandException;
import org.axonframework.axonserver.connector.event.util.EventStoreClientException;
import org.axonframework.axonserver.connector.query.RemoteQueryException;
import org.axonframework.axonserver.connector.util.ExceptionSerializer;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.commandhandling.distributed.CommandDispatchException;
import org.axonframework.common.AxonException;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.messaging.EventPublicationFailedException;
import org.axonframework.queryhandling.NoHandlerForQueryException;
import org.axonframework.queryhandling.QueryExecutionException;

import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;


/**
 * Converts an Axon Server Error to the relevant Axon framework exception.
 */
public enum ErrorCode {
    // Generic errors processing client request
    AUTHENTICATION_TOKEN_MISSING("AXONIQ-1000", AxonServerException::new),
    AUTHENTICATION_INVALID_TOKEN("AXONIQ-1001", AxonServerException::new),
    NODE_IS_REPLICA("AXONIQ-1100", AxonServerException::new),

    //Event publishing errors
    INVALID_SEQUENCE("AXONIQ-2000", (code, error) -> new EventPublicationFailedException(error.getMessage(), new AxonServerException(code, error))),
    NO_MASTER_AVAILABLE("AXONIQ-2100", (code, error) -> new EventPublicationFailedException(error.getMessage(), new AxonServerException(code, error))),

    //Communication errors
    CONNECTION_FAILED("AXONIQ-6000", AxonServerException::new),
    PAYLOAD_TOO_LARGE("AXONIQ-6001", AxonServerException::new),

    // Command errors
    COMMAND_EXECUTION_ERROR("AXONIQ-7000", (code, error) -> new CommandExecutionException(error.getMessage(),  new RemoteCommandException(code, error))),
    NO_HANDLER_FOR_COMMAND("AXONIQ-7001", (code, error) -> new NoHandlerForCommandException(error.getMessage())),
    COMMAND_DISPATCH_ERROR("AXONIQ-7002", (code, error) -> new CommandDispatchException(error.getMessage())),

    //Query errors
    QUERY_EXECUTION_ERROR("AXONIQ-8000", (code, error) -> new QueryExecutionException(error.getMessage(), new RemoteQueryException(code, error))),
    NO_HANDLER_FOR_QUERY("AXONIQ-8001", (code,error) -> new NoHandlerForQueryException(error.getMessage())),

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
    private final BiFunction<String, ErrorMessage, ? extends AxonException> exceptionFn;

    ErrorCode(String errorCode, BiFunction<String, ErrorMessage, ? extends AxonException> exceptionFn) {
        this.errorCode = errorCode;
        this.exceptionFn = exceptionFn;
    }

    private static AxonException convert(String code, Throwable t) {
        return Arrays.stream(values()).filter(mapping -> mapping.errorCode.equals(code))
              .findFirst()
              .orElse(OTHER)
              .convert(ExceptionSerializer.serialize("", t));
    }

    public static AxonException convert(Throwable t) {
        if( t instanceof EventStoreClientException) {
            return convert(((EventStoreClientException)t).getCode(), t);
        }
        if( t instanceof TimeoutException) {
            return new org.axonframework.messaging.ExecutionException("Timeout while executing request", t);
        }
        return new AxonServerException(OTHER.errorCode, t.getMessage());
    }


    public String errorCode() {
        return errorCode;
    }

    public AxonException convert(ErrorMessage errorMessage){
        return exceptionFn.apply(errorCode,errorMessage);
    }
}
