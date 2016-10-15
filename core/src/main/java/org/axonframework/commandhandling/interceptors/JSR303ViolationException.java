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

package org.axonframework.commandhandling.interceptors;

import org.axonframework.commandhandling.StructuralCommandValidationFailedException;
import org.axonframework.common.AxonNonTransientException;

import java.util.Iterator;
import java.util.Set;
import javax.validation.ConstraintViolation;
import java.util.TreeSet;
import javax.validation.ConstraintViolation;

/**
 * Specialized version of the StructuralCommandValidationFailedException that provides a set of JSR303 constraint
 * violations that provide details about the exact failure of the command.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class JSR303ViolationException extends StructuralCommandValidationFailedException {

    private static final long serialVersionUID = -1585918243998401966L;

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    private final Set<ConstraintViolation<Object>> violations;

    /**
     * Initializes an exception with the given <code>message</code> and <code>violations</code>.
     *
     * @param violations The violations that were detected
     */
    public JSR303ViolationException(Set<ConstraintViolation<Object>> violations) {
        super("One or more JSR303 constraints were violated: " + LINE_SEPARATOR + convert(violations));
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
     * Convert the violations to a human readable format, sorted by class and property e.g.
     * <pre>
     *   property ab in class my.some.Klass may not be null
     *   property cd in class my.some.OtherClass length must be between 0 and 3
     *   property cd in class my.some.OtherClass must match "ab.*"
     *
     * </pre>
     * <pre>property notNull in class my.some.TheClass may not be null</pre>
     */
    protected static String convert(Set<ConstraintViolation<Object>> violations) {
        // sort the violations on bean class and property name
        Set<String> sortedViolations = new TreeSet<String>();
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
                sb.append(LINE_SEPARATOR);
            }
        }
        return sb.toString();
    }
}
