/*
 * Copyright (c) 2010-2012. Axon Framework
 *
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

package org.axonframework.commandhandling.distributed.jgroups;

import org.axonframework.common.AxonException;

/**
 * Exception indicating that a CommandBusConnector failed to connect to other instances.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ConnectionFailedException extends AxonException {

    private static final long serialVersionUID = -5193897502103028801L;

    /**
     * Initializes the exception using the given <code>message</code>.
     *
     * @param message The message describing the exception
     */
    public ConnectionFailedException(String message) {
        super(message);
    }

    /**
     * Initializes the exception using the given <code>message</code> and <code>cause</code>.
     *
     * @param message The message describing the exception
     * @param cause   The underlying cause of the exception
     */
    public ConnectionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
