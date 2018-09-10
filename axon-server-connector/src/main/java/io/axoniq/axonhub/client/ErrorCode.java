/*
 * Copyright (c) 2018. AxonIQ
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.axoniq.axonhub.client;

import io.axoniq.axonhub.client.event.util.EventStoreClientException;
import org.axonframework.commandhandling.model.AggregateRolledBackException;
import org.axonframework.commandhandling.model.ConcurrencyException;
import org.axonframework.common.AxonException;
import org.axonframework.eventsourcing.eventstore.EventStoreException;

import java.util.Arrays;
import java.util.concurrent.TimeoutException;


/**
 * Converts an EventStoreClientException to the relevant Axon framework exception.
 */
public enum ErrorCode {
    // Generic errors processing client request
    AUTHENTICATION_TOKEN_MISSING("AXONIQ-1000", EventStoreException.class),
    AUTHENTICATION_INVALID_TOKEN("AXONIQ-1001", EventStoreException.class),
    NODE_IS_REPLICA("AXONIQ-1100", EventStoreException.class),

    // Input errors
    INVALID_SEQUENCE("AXONIQ-2000", ConcurrencyException.class),
    PAYLOAD_TOO_LARGE("AXONIQ-2001", EventStoreException.class),
    NO_MASTER_AVAILABLE("AXONIQ-2100", EventStoreException.class),

    CONNECTION_TO_AXONDB_FAILED("AXONIQ-6000", EventStoreException.class),

    // Internal errors
    DATAFILE_READ_ERROR( "AXONIQ-9000", EventStoreException.class),
    INDEX_READ_ERROR( "AXONIQ-9001", EventStoreException.class),
    DATAFILE_WRITE_ERROR( "AXONIQ-9100", EventStoreException.class),
    INDEX_WRITE_ERROR( "AXONIQ-9101", EventStoreException.class),
    DIRECTORY_CREATION_FAILED("AXONIQ-9102", EventStoreException.class),
    VALIDATION_FAILED( "AXONIQ-9200", EventStoreException.class),
    TRANSACTION_ROLLED_BACK( "AXONIQ-9900", AggregateRolledBackException.class),
    OTHER( "AXONIQ-0001", EventStoreException.class),
    ;

    private final String code;
    private final Class exceptionClass;

    ErrorCode(String code, Class<? extends AxonException> exceptionClass) {
        this.code = code;
        this.exceptionClass = exceptionClass;
    }


    public static Class<? extends AxonException> lookupExceptionClass(String code) {
        return Arrays.stream(values()).filter(mapping -> mapping.code.equals(code))
                .map(mapping -> mapping.exceptionClass)
                .findFirst().orElse(OTHER.exceptionClass);
    }

    public static AxonException convert(Throwable t) {
        if( t instanceof EventStoreClientException) {
            Class<? extends AxonException> clazz = lookupExceptionClass(((EventStoreClientException)t).getCode());
            try {
                return clazz.getDeclaredConstructor(String.class, Throwable.class).newInstance(t.getMessage(), t.getCause());
            } catch (Exception ex) {
            }
        }

        if( t instanceof TimeoutException) {
            return new org.axonframework.messaging.ExecutionException("Timeout while executing request", t);
        }
        return new EventStoreException(t.getMessage(), t);
    }

    public static ErrorCode resolve(Throwable throwable) {
        return OTHER;
    }

    public String errorCode() {
        return code;
    }
}
