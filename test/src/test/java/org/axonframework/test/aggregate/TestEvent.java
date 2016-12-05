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
