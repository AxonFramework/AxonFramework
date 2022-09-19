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
 * A {@link CommandMessageFilter} implementation which filters {@link CommandMessage}s by the {@link
 * CommandMessage#getCommandName()}. It can be combined with other {@link CommandMessageFilter} instances in an
 * efficient manner.
 *
 * @author Koen Lavooij
 * @since 3.0
 */
public class CommandNameFilter implements CommandMessageFilter {

    private final Set<String> commandNames;

    /**
     * Initializes a {@link CommandNameFilter} for the given set of {@code commandNames}.
     *
     * @param commandNames commands that can be handled
     */
    @ConstructorProperties("commandNames")
    public CommandNameFilter(@JsonProperty("commandNames") Set<String> commandNames) {
        this.commandNames = new HashSet<>(commandNames);
    }

    /**
     * Initializes a {@link CommandNameFilter} for a single command name.
     *
     * @param commandName command that can be handled
     */
    public CommandNameFilter(String commandName) {
        this(Collections.singleton(commandName));
    }

    @Override
    public boolean matches(@Nonnull CommandMessage<?> commandMessage) {
        return commandNames.contains(commandMessage.getCommandName());
    }

    @Override
    public CommandMessageFilter negate() {
        return new DenyCommandNameFilter(commandNames);
    }

    @Override
    public CommandMessageFilter and(@Nonnull CommandMessageFilter other) {
        if (other instanceof CommandNameFilter) {
            return new CommandNameFilter(commandNames.stream()
                                                     .filter(((CommandNameFilter) other).commandNames::contains)
                                                     .collect(Collectors.toSet()));
        } else {
            return (t) -> matches(t) && other.matches(t);
        }
    }

    @Override
    public CommandMessageFilter or(@Nonnull CommandMessageFilter other) {
        if (other instanceof CommandNameFilter) {
            return new CommandNameFilter(
                    Stream.concat(
                                  commandNames.stream(),
                                  ((CommandNameFilter) other).commandNames.stream())
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
        CommandNameFilter that = (CommandNameFilter) o;
        return Objects.equals(commandNames, that.commandNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commandNames);
    }

    @Override
    public String toString() {
        return "CommandNameFilter{" +
                "commandNames=" + commandNames +
                '}';
    }
}
