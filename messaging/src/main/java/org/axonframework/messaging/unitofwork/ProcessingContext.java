package org.axonframework.messaging.unitofwork;

/**
 * @author Awesome People
 */
public interface ProcessingContext extends ProcessingLifecycle {

    Resources resources();

    boolean isStarted();

    boolean isError();

    boolean isCommitted();

    boolean isCompleted();

}
