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

package org.axonframework.javax.messaging.interceptors;

import org.axonframework.common.AxonNonTransientException;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import javax.validation.ConstraintViolation;

/**
 * Exception indicating that a {@link org.axonframework.messaging.Message} has been refused due to a structural
 * validation failure. Typically, resending the same message will result in the exact same exception.
 * <p>
 * Provides a set of JSR303 {@link ConstraintViolation}s that provide details about the exact failure of the message.
 *
 * @author Allard Buijze
 * @since 1.1
 * @deprecated in favor of using {@link org.axonframework.messaging.interceptors.JSR303ViolationException} which moved
 * to jakarta.
 */
@Deprecated
public class JSR303ViolationException extends AxonNonTransientException {

    private static final long serialVersionUID = -1585918243998401966L;

    private final Set<ConstraintViolation<Object>> violations;

    /**
     * Initializes an exception with the given {@code message} and {@code violations}.
     *
     * @param violations the violations that were detected
     */
    public JSR303ViolationException(Set<ConstraintViolation<Object>> violations) {
        super("One or more JSR303 constraints were violated: " + System.lineSeparator() + convert(violations));
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

    /**
     * Convert the violations to a human readable format, sorted by class and property e.g.:
     * <pre>
     *   property ab in class my.some.Klass may not be null
     *   property cd in class my.some.OtherClass length must be between 0 and 3
     *   property cd in class my.some.OtherClass must match "ab.*"
     *   property notNull in class my.some.TheClass may not be null
     * </pre>
     *
     * @param violations set of violations that were detected when the exception was thrown
     * @return a human readable string describing the violations
     */
    @SuppressWarnings("DuplicatedCode")
    protected static String convert(Set<ConstraintViolation<Object>> violations) {
        // sort the violations on bean class and property name
        Set<String> sortedViolations = new TreeSet<>();
        for (ConstraintViolation<Object> violation : violations) {
            String msg = "property " + violation.getPropertyPath();
            msg += " in " + violation.getRootBeanClass();
            msg += " " + violation.getMessage();
            sortedViolations.add(msg);
        }
        StringBuilder sb = new StringBuilder();
        Iterator<String> it = sortedViolations.iterator();
        while (it.hasNext()) {
            sb.append(it.next());
            if (it.hasNext()) {
                sb.append(System.lineSeparator());
            }
        }
        return sb.toString();
    }
}
