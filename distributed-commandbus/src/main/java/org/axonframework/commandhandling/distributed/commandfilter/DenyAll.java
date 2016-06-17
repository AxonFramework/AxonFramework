package org.axonframework.commandhandling.distributed.commandfilter;

import org.axonframework.commandhandling.CommandMessage;

import java.io.Serializable;
import java.util.function.Predicate;

public class DenyAll implements Predicate<CommandMessage>, Serializable {
    public static final DenyAll INSTANCE = new DenyAll();

    DenyAll() {
    }

    @Override
    public boolean test(CommandMessage commandMessage) {
        return false;
    }

    @Override
    public Predicate<CommandMessage> and(Predicate<? super CommandMessage> other) {
        return this;
    }

    @Override
    public Predicate<CommandMessage> negate() {
        return AcceptAll.INSTANCE;
    }

    @Override
    public Predicate<CommandMessage> or(Predicate<? super CommandMessage> other) {
        return (Predicate) other;
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && (obj instanceof DenyAll);
    }

    @Override
    public String toString() {
        return "DenyAll{}";
    }


    @Override
    public int hashCode() {
        return 13;
    }
}
