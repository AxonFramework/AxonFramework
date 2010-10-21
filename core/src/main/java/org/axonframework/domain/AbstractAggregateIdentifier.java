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

package org.axonframework.domain;

/**
 * Abstract implementation of the aggregate identifier providing basic functionality as defined by {@link
 * AggregateIdentifier}.
 * <p/>
 * This implementation contains an implementation of the {@link #compareTo(AggregateIdentifier)}, {@link
 * #equals(Object)}, {@link #hashCode()} and {@link #toString()} methods.
 * <p/>
 * The {@link #compareTo(AggregateIdentifier)} methods returns the result of the comparison of both identifier's string
 * representations. For numerical values, this may not be a suitable comparison. For example, 3 would be evaluated as
 * being larger than 10, when compared as Strings.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class AbstractAggregateIdentifier implements AggregateIdentifier, Comparable<AggregateIdentifier> {

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation returns the result of {@link #asString()}.
     */
    @Override
    public String toString() {
        return asString();
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation returns the hashCode of the string representation of the identifier.
     */
    @Override
    public int hashCode() {
        return asString().hashCode();
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation compared the string representations of both identifiers.
     */
    @Override
    public int compareTo(AggregateIdentifier other) {
        return asString().compareTo(other.asString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AggregateIdentifier)) {
            return false;
        }
        AggregateIdentifier that = (AggregateIdentifier) o;
        return asString().equals(that.asString());
    }
}
