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

package org.axonframework.modelling.command;

import org.axonframework.common.AxonException;

/**
 * Exception indicating that a required identifier is missing in the processed message.
 *
 * @author Stefan Andjelkovic
 * @since 4.6.0
 */
public class IdentifierMissingException extends AxonException {

    /**
     * Constructs an exception based on the given {@code message}.
     *
     * @param message The description of this {@link IdentifierMissingException}.
     */
    public IdentifierMissingException(String message) {
        super(message);
    }
}
