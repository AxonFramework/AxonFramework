/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.eventhandling.replay;

import org.axonframework.common.AxonException;

/**
 * Exception indicating that a replay task has failed.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ReplayFailedException extends AxonException {

    private static final long serialVersionUID = -6513607934546330054L;

    /**
     * Initialize the exception with given <code>message</code> and <code>cause</code>
     *
     * @param message The message describing the failure
     * @param cause   The cause of the failure
     */
    public ReplayFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
