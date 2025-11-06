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

package org.axonframework.common.jdbc;

import org.axonframework.common.AxonTransientException;

/**
 * Exception indicating an error occurred while interacting with a JDBC resource.
 *
 * @author Allard Buijze
 * @since 2.2
 */
public class JdbcException extends AxonTransientException {

    /**
     * Initialize the exception with given {@code message} and {@code cause}
     *
     * @param message The message describing the error
     * @param cause   The cause of the error
     */
    public JdbcException(String message, Throwable cause) {
        super(message, cause);
    }
}
