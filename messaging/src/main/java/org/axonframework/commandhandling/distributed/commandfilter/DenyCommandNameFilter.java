/*
 * Copyright (c) 2010-2022. Axon Framework
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

/**
 * A {@link CommandMessageFilter} implementation which denies {@link CommandMessage}s based on their {@link
 * CommandMessage#getCommandName()}. It can be combined with other {@link CommandMessageFilter} instances in an
 * efficient manner.
 *
 * @author Koen Lavooij
 * @author Allard Buijze
 * @since 3.0
 */
public class DenyCommandNameFilter implements CommandMessageFilter {

    private final Set<String> commandNames;

    /**
     * Initializes a {@link DenyCommandNameFilter} from the given set of {@code commandNames}. Commands with a name that
     * is present in this set will be blocked by this filter.
     *
     * @param commandNames the names of commands blocked by this filter
     */
    @ConstructorProperties("commandNames")
    public DenyCommandNameFilter(@JsonProperty("commandNames") Set<String> commandNames) {
        this.commandNames = new HashSet<>(commandNames);
    }

    /**
     * Initializes a {@link DenyCommandNameFilter} for a single {@code commandName}. Commands with a name equal to the
     * given commandName will be blocked by this filter.
     *
     * @param commandName the name of the command blocked by this filter
     */
    public DenyCommandNameFilter(String commandName) {
        this.commandNames = Collections.singleton(commandName);
    }

    @Override
    public boolean matches(@Nonnull CommandMessage<?> commandMessage) {
        return !commandNames.contains(commandMessage.getCommandName());
    }

    @Override
    public CommandMessageFilter and(@Nonnull CommandMessageFilter other) {
        if (other instanceof DenyCommandNameFilter) {
            return new DenyCommandNameFilter(
                    Stream.concat(commandNames.stream(), ((DenyCommandNameFilter) other).commandNames.stream())
                          .collect(Collectors.toSet()));
        } else {
            return new AndCommandMessageFilter(this, other);
        }
    }

    @Override
    public CommandMessageFilter or(@Nonnull CommandMessageFilter other) {
        if (other instanceof DenyCommandNameFilter) {
            return new DenyCommandNameFilter(
                    commandNames.stream().filter(((DenyCommandNameFilter) other).commandNames::contains)
                                .collect(Collectors.toSet()));
        } else {
            return new OrCommandMessageFilter(this, other);
        }
    }

    @JsonGetter
    private Set<String> getCommandNames() {
        return commandNames;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DenyCommandNameFilter that = (DenyCommandNameFilter) o;
        return Objects.equals(commandNames, that.commandNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commandNames);
    }

    @Override
    public String toString() {
        return "DenyCommandNameFilter{" + "commandNames=" + commandNames + '}';
    }
}
