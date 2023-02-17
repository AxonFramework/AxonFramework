/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.eventhandling.tokenstore;

import org.axonframework.common.AxonTransientException;

/**
 * Exception indicating that a TokenStore implementation was unable determine its identifier based on the underlying
 * storage.
 *
 * @author Allard Buijze
 * @see TokenStore#retrieveStorageIdentifier()
 * @since 4.3
 */
public class UnableToRetrieveIdentifierException extends AxonTransientException {

    /**
     * Initialize the exception using given {@code message} and {@code cause}.
     *
     * @param message A message describing the exception
     * @param cause   The underlying cause of the exception
     */
    public UnableToRetrieveIdentifierException(String message, Throwable cause) {
        super(message, cause);
    }
}
