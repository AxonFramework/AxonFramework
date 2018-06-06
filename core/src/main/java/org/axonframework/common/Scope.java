package org.axonframework.common;

import java.util.Deque;
import java.util.LinkedList;

/**
 * TODO add javadoc
 */
public abstract class Scope {

    private static final ThreadLocal<Deque<Scope>> CURRENT_SCOPE = ThreadLocal.withInitial(LinkedList::new);

    public void start() {
        CURRENT_SCOPE.get().push(this);
    }

    public void end() {
        if (this != CURRENT_SCOPE.get().peek()) {
            throw new IllegalStateException("");
        }
        CURRENT_SCOPE.get().pop();
    }
}
