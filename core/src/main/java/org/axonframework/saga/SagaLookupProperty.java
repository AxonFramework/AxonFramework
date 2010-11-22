/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.saga;

import java.io.Serializable;

/**
 * A combination of key and value by which a Saga can be found. Two lookup properties are considered equal when both
 * their key and value are equal.
 * <p/>
 * For example, a Saga managing Orders could have a LookupProperty with key &quot;orderId&quot; and the order identifier
 * as value. A single lookup property can lead to multiple Saga's, and a single Saga can have multiple lookup properties
 * pointed to it.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class SagaLookupProperty implements Serializable {

    private static final long serialVersionUID = 3573690125021875389L;

    private final String propertyKey;
    private final Object propertyValue;

    /**
     * Creates a Lookup Property instance with the given <code>key</code> and <code>value</code>.
     *
     * @param key   The key of the lookup property. Usually indicates where the value comes from.
     * @param value The value corresponding to the key of the lookup. It is highly recommended to only use serializable
     *              values.
     */
    public SagaLookupProperty(String key, Object value) {
        this.propertyKey = key;
        this.propertyValue = value;
    }

    /**
     * Returns the key of this lookup property. The key usually indicates where the properties' value comes from.
     *
     * @return the key of this lookup property
     */
    public String getPropertyKey() {
        return propertyKey;
    }

    /**
     * Returns the value of this lookup property.
     *
     * @return the value of this lookup property.
     */
    public Object getPropertyValue() {
        return propertyValue;
    }

    @SuppressWarnings({"RedundantIfStatement"})
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SagaLookupProperty that = (SagaLookupProperty) o;

        if (!propertyKey.equals(that.propertyKey)) {
            return false;
        }
        if (!propertyValue.equals(that.propertyValue)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = propertyKey.hashCode();
        result = 31 * result + propertyValue.hashCode();
        return result;
    }
}
