package org.axonframework.domain;

/**
 * Callback that allows components to be notified of an event being registered with an Aggregate. It also allows these
 * components to alter the generated event, before it is passed to the aggregate's Event Handler (if it is an Event
 * Sourced Aggregate).
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface EventRegistrationCallback {

    /**
     * Invoked when an Aggregate registers an Event for publication. The simplest implementation will simply return
     * the given <code>event</code>.
     *
     * @param event The event registered for publication
     * @param <T>   The type of payload
     * @return the message to actually publish. May <em>not</em> be <code>null</code>.
     */
    <T> DomainEventMessage<T> onRegisteredEvent(DomainEventMessage<T> event);
}
