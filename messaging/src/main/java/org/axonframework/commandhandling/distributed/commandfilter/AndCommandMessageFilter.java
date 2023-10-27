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

package org.axonframework.commandhandling.distributed.commandfilter;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.CommandMessageFilter;

import java.beans.ConstructorProperties;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * A {@link CommandMessageFilter} implementation that matches whenever both supplied {@link CommandMessageFilter}
 * instances match.
 *
 * @author Allard Buijze
 * @since 4.0
 */
public class AndCommandMessageFilter implements CommandMessageFilter {

    private final CommandMessageFilter first;
    private final CommandMessageFilter second;

    /**
     * Initialize the filter to match when both the {@code first} and the {@code second} filter match.
     *
     * @param first  the first filter to match
     * @param second the second filter to match
     */
    @ConstructorProperties({"first", "second"})
    public AndCommandMessageFilter(@JsonProperty("first") CommandMessageFilter first,
                                   @JsonProperty("second") CommandMessageFilter second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean matches(@Nonnull CommandMessage<?> commandMessage) {
        return first.matches(commandMessage) && second.matches(commandMessage);
    }

    @JsonGetter
    private CommandMessageFilter getFirst() {
        return first;
    }

    @JsonGetter
    private CommandMessageFilter getSecond() {
        return second;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AndCommandMessageFilter that = (AndCommandMessageFilter) o;
        return Objects.equals(first, that.first) &&
                Objects.equals(second, that.second);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }

    @Override
    public String toString() {
        return "AndCommandMessageFilter{" +
                "first=" + first +
                ", second=" + second +
                '}';
    }
}
