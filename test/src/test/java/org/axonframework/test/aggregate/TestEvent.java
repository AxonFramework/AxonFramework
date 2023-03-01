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

package org.axonframework.test.aggregate;

import java.util.Map;
import java.util.Objects;

class TestEvent {

    private final Object aggregateIdentifier;
    private final Map<String, Object> values;

    public TestEvent(Object aggregateIdentifier, Map<String, Object> values) {
        this.aggregateIdentifier = aggregateIdentifier;
        this.values = values;
    }

    public Object getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestEvent testEvent = (TestEvent) o;
        return Objects.equals(aggregateIdentifier, testEvent.aggregateIdentifier) &&
                Objects.equals(values, testEvent.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aggregateIdentifier, values);
    }

}
