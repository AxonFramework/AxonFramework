package org.axonframework.commandhandling.distributed.commandfilter;

import org.axonframework.commandhandling.CommandMessage;

import java.io.Serializable;
import java.util.function.Predicate;

public class AcceptAll implements Predicate<CommandMessage>, Serializable {
    public static final AcceptAll INSTANCE = new AcceptAll();

    AcceptAll() {
    }

    @Override
    public boolean test(CommandMessage commandMessage) {
        return true;
    }

    @Override
    public Predicate<CommandMessage> and(Predicate<? super CommandMessage> other) {
        return (Predicate<CommandMessage>) other;
    }

    @Override
    public Predicate<CommandMessage> negate() {
        return DenyAll.INSTANCE;
    }

    @Override
    public Predicate<CommandMessage> or(Predicate<? super CommandMessage> other) {
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && (obj instanceof AcceptAll);
    }

    @Override
    public int hashCode() {
        return 7;
    }

    @Override
    public String toString() {
        return "AcceptAll{}";
    }
}
