package org.axonframework.springboot.autoconfig.context;

import java.util.Objects;

public class AnimalRenamedEvent {

    private final String aggregateId;
    private final String rename;

    public AnimalRenamedEvent(String aggregateId, String rename) {
        this.aggregateId = aggregateId;
        this.rename = rename;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getRename() {
        return rename;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnimalRenamedEvent that = (AnimalRenamedEvent) o;
        return Objects.equals(aggregateId, that.aggregateId) && Objects.equals(rename, that.rename);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aggregateId, rename);
    }

    @Override
    public String toString() {
        return "AnimalRenamedEvent{" + "aggregateId='" + aggregateId + '\'' + ", rename='" + rename + '\'' + '}';
    }
}
