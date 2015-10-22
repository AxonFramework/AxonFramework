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

package org.axonframework.messaging.interceptors;

import org.axonframework.common.AxonNonTransientException;

import javax.validation.ConstraintViolation;
import java.util.Set;

/**
 * Exception indicating that a Message has been refused due to a structural validation failure. Typically, resending
 * the same message will result in the exact same exception.
 * <p/>
 * Provides a set of JSR303 constraint violations that provide details about the exact failure of the message.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class JSR303ViolationException extends AxonNonTransientException {

    private static final long serialVersionUID = -1585918243998401966L;
    private final Set<ConstraintViolation<Object>> violations;

    /**
     * Initializes an exception with the given <code>message</code> and <code>violations</code>.
     *
     * @param message    A descriptive message of the failure
     * @param violations The violations that were detected
     */
    public JSR303ViolationException(String message, Set<ConstraintViolation<Object>> violations) {
        super(message);
        this.violations = violations;
    }

    /**
     * Returns the violations that were detected when this exception was thrown.
     *
     * @return the violations that were detected when this exception was thrown
     */
    public Set<ConstraintViolation<Object>> getViolations() {
        return violations;
    }
}
