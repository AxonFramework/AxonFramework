package org.axonframework.commandhandling.distributed.commandfilter;

import org.axonframework.commandhandling.CommandMessage;

import java.io.Serializable;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CommandNameFilter implements Predicate<CommandMessage>, Serializable {
    final Set<String> commandNames;

    public CommandNameFilter(Set<String> commandNames) {
        this.commandNames = new HashSet<>(commandNames);
    }

    public CommandNameFilter(String commandName) {
        this(Collections.singleton(commandName));
    }

    @Override
    public boolean test(CommandMessage commandMessage) {
        return commandNames.contains(commandMessage.getCommandName());
    }

    @Override
    public Predicate<CommandMessage> negate() {
        return new DenyCommandNameFilter(commandNames);
    }

    @Override
    public Predicate<CommandMessage> and(Predicate<? super CommandMessage> other) {
        if (other instanceof CommandNameFilter) {
            Set<String> otherCommandNames = ((CommandNameFilter) other).commandNames;
            return new CommandNameFilter(commandNames
                    .stream()
                    .filter(otherCommandNames::contains)
                    .collect(Collectors.toSet()));
        } else {
            return (t) -> test(t) && other.test(t);
        }
    }

    @Override
    public Predicate<CommandMessage> or(Predicate<? super CommandMessage> other) {
        if (other instanceof CommandNameFilter) {
            return new CommandNameFilter(
                    Stream.concat(
                            commandNames.stream(),
                            ((CommandNameFilter) other).commandNames.stream())
                            .collect(Collectors.toSet()));
        } else {
            return (t) -> test(t) || other.test(t);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
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
