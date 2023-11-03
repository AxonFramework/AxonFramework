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
