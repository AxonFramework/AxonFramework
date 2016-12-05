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

package org.axonframework.test.matchers;

import org.axonframework.common.AxonNonTransientException;

/**
 * Exception indicating that an error occurred that prevented successful execution of a matcher. This exception does
 * not mean an actual instance does not match the expected instance. It means the matcher was unable to perform the
 * match due to external factors.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class MatcherExecutionException extends AxonNonTransientException {

    private static final long serialVersionUID = 2867528683103491260L;

    /**
     * Construct the exception with the given {@code message}.
     *
     * @param message the message describing the cause
     */
    public MatcherExecutionException(String message) {
        super(message);
    }

    /**
     * Construct the exception with the given {@code message} and {@code cause}.
     *
     * @param message the message describing the cause
     * @param cause   the underlying cause
     */
    public MatcherExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
