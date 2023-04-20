package org.axonframework.springboot.autoconfig.context;

import java.util.Objects;

public class DogCreatedEvent {

    private final String aggregateId;
    private final String name;

    public DogCreatedEvent(String aggregateId, String name) {
        this.aggregateId = aggregateId;
        this.name = name;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DogCreatedEvent that = (DogCreatedEvent) o;
        return Objects.equals(aggregateId, that.aggregateId) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aggregateId, name);
    }

    @Override
    public String toString() {
        return "DogCreatedEvent{aggregateId='" + aggregateId + '\'' + ", name='" + name + '\'' + '}';
    }
}
