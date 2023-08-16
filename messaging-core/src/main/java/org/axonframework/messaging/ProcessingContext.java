package org.axonframework.messaging;

// TODO why does ProcessingContext extends ProcessingLifecycle ?
public interface ProcessingContext extends ProcessingLifecycle {

    Resources resources(ResourceScope scope);

    boolean isStarted();

    boolean isRolledBack();

    boolean isCommitted();

    boolean isCompleted();

    enum ResourceScope {
        LOCAL,
        INHERITED,
        SHARED
    }
}
