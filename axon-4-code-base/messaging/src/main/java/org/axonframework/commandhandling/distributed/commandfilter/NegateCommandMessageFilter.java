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
 * A {@link CommandMessageFilter} implementation that negates the result of another {@link CommandMessageFilter}
 * instance.
 *
 * @author Allard Buijze
 * @since 4.0
 */
public class NegateCommandMessageFilter implements CommandMessageFilter {

    private final CommandMessageFilter filter;

    /**
     * Initialize a {@link CommandMessageFilter} that negates results of the the given {@code filter}.
     *
     * @param filter the filter to negate
     */
    @ConstructorProperties("filter")
    public NegateCommandMessageFilter(@JsonProperty("filter") CommandMessageFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean matches(@Nonnull CommandMessage<?> commandMessage) {
        return !filter.matches(commandMessage);
    }

    @JsonGetter
    private CommandMessageFilter getFilter() {
        return filter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NegateCommandMessageFilter that = (NegateCommandMessageFilter) o;
        return Objects.equals(filter, that.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filter);
    }

    @Override
    public String toString() {
        return "NegateCommandMessageFilter{" +
                "filter=" + filter +
                '}';
    }
}
