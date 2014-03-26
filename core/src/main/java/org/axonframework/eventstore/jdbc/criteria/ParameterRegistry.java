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

package org.axonframework.eventstore.jdbc.criteria;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The registry that keeps track of parameterized values. This ensures proper escaping of literal values.
 *
 * @author Allard Buijze
 * @author Kristian Rosenvold
 *
 * @since 2.2
 */
public class ParameterRegistry {

    private final List<Object> parameters = new ArrayList<Object>();

    /**
     * Registers the given <code>expression</code> as parameter and returns the value to use to refer to this
     * expression.
     *
     * @param expression The expression to parameterize in the query
     * @return The placeholder to use in the query to refer to the given parameter
     */
    public String register(Object expression) {
        parameters.add(expression);
        return "?";
    }

    /**
     * Returns a map containing the key-value pairs, where each key is the parameter name, and the value the expression
     * to be inserted as parameter.
     *
     * @return a map containing the parameters
     */
    public List<Object> getParameters() {
        return Collections.unmodifiableList(parameters);
    }
}
