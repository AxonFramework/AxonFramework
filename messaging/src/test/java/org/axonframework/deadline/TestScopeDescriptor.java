/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.deadline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.axonframework.messaging.ScopeDescriptor;

import java.util.Objects;

public class TestScopeDescriptor implements ScopeDescriptor {

    private final String type;
    private Object identifier;

    @JsonCreator
    public TestScopeDescriptor(@JsonProperty("type") String type, @JsonProperty("identifier") Object identifier) {
        this.type = type;
        this.identifier = identifier;
    }

    public String getType() {
        return type;
    }

    public Object getIdentifier() {
        return identifier;
    }

    @Override
    public String scopeDescription() {
        return String.format("TestScopeDescriptor for type [%s] and identifier [%s]", type, identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, identifier);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final TestScopeDescriptor other = (TestScopeDescriptor) obj;
        return Objects.equals(this.type, other.type)
                && Objects.equals(this.identifier, other.identifier);
    }

    @Override
    public String toString() {
        return "TestScopeDescriptor{" +
                "type=" + type +
                ", identifier='" + identifier + '\'' +
                '}';
    }
}
