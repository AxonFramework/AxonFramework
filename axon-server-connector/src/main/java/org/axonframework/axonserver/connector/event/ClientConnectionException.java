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

package org.axonframework.axonserver.connector.event;

/**
 * Exception describing an error reported by the AxonIQ Event Store Client.
 */
public class ClientConnectionException extends RuntimeException {

    /**
     * Initialize the exception with given {@code message} and {@code cause}
     *
     * @param message A message describing the error
     * @param cause   The cause of the exception
     */
    public ClientConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
