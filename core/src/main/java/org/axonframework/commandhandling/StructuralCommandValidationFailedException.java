/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.commandhandling;

import org.axonframework.common.AxonNonTransientException;

/**
 * Exception indicating that a Command has been refused due to a structural validation failure. Typically, resending
 * the same command will result in the exact same exception.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class StructuralCommandValidationFailedException extends AxonNonTransientException {

    private static final long serialVersionUID = -570442112223210700L;

    /**
     * Initializes an exception with the given <code>message</code>.
     *
     * @param message A descriptive message of the failure
     */
    public StructuralCommandValidationFailedException(String message) {
        super(message);
    }
}
