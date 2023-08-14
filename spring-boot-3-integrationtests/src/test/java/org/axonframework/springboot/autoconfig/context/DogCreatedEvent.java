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
