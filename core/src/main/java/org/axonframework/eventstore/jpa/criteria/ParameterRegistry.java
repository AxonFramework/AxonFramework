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

package org.axonframework.eventstore.jpa.criteria;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The registry that keeps track of parameterized values. This ensures proper escaping of literal values.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ParameterRegistry {

    private final Map<String, Object> parameters = new HashMap<String, Object>();

    /**
     * Registers the given <code>expression</code> as parameter and returns the value to use to refer to this
     * expression.
     *
     * @param expression The expression to parameterize in the query
     * @return The placeholder to use in the query to refer to the given parameter
     */
    public String register(Object expression) {
        String paramName = "param" + parameters.size();
        parameters.put(paramName, expression);
        return ":" + paramName;
    }

    /**
     * Returns a map containing the key-value pairs, where each key is the parameter name, and the value the expression
     * to be inserted as parameter.
     *
     * @return a map containing the parameters
     */
    public Map<String, Object> getParameters() {
        return Collections.unmodifiableMap(parameters);
    }
}
