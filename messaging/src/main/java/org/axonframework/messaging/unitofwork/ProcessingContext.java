package org.axonframework.messaging.unitofwork;

/**
 * @author Awesome People
 */
public interface ProcessingContext extends ProcessingLifecycle {

    Resources resources();

    boolean isStarted();

    boolean isRolledBack();

    boolean isCommitted();

    boolean isCompleted();

}
