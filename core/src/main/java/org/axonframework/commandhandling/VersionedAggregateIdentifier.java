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

package org.axonframework.commandhandling;

/**
 * Structure that holds an Aggregate Identifier and an expected version of an aggregate.
 *
 * @author Allard Buijze
 * @since 1.2
 */
public class VersionedAggregateIdentifier {

    private static final long serialVersionUID = -5678446021335130329L;

    private final Object identifier;
    private final Long version;

    /**
     * Initializes a VersionedAggregateIdentifier with the given {@code identifier} and {@code version}.
     *
     * @param identifier The identifier of the targeted aggregate
     * @param version    The expected version of the targeted aggregate, or {@code null} if the version is irrelevant
     */
    public VersionedAggregateIdentifier(Object identifier, Long version) {
        this.identifier = identifier;
        this.version = version;
    }

    /**
     * Returns the identifier of the targeted Aggregate.
     *
     * @return the identifier of the targeted Aggregate
     */
    public Object getIdentifier() {
        return identifier;
    }

    /**
     * Returns the version of the targeted Aggregate, or {@code null} if the version is irrelevant.
     *
     * @return the version of the targeted Aggregate
     */
    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        VersionedAggregateIdentifier that = (VersionedAggregateIdentifier) o;

        if (!identifier.equals(that.identifier)) {
            return false;
        }
        if (version != null ? !version.equals(that.version) : that.version != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = identifier.hashCode();
        result = 31 * result + (version != null ? version.hashCode() : 0);
        return result;
    }
}
