/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.axonserver.connector.event.util;

/**
 * Exception originating from an Event Store client implementation.
 *
 * @author Marc Gathier
 * @since 4.0
 * @deprecated in through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">AxonServer java
 * connector</a>
 */
@Deprecated
public class EventStoreClientException extends RuntimeException {

    private final String code;

    /**
     * Constructs a {@link EventStoreClientException} using the given {@code code} and {@code message}.
     *
     * @param code    the exception code to include in the exception
     * @param message the exception message to include in the exception
     */
    public EventStoreClientException(String code, String message) {
        this(code, message, null);
    }

    /**
     * Constructs a {@link EventStoreClientException} using the given {@code code}, {@code message} and {@code cause}.
     *
     * @param code    the exception code to include in the exception
     * @param message the exception message to include in the exception
     * @param cause   the original cause to based this exception on
     */
    public EventStoreClientException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * Returns the error {@code code} of this exception.
     *
     * @return the error {@code code} of this exception
     */
    public String getCode() {
        return code;
    }
}
