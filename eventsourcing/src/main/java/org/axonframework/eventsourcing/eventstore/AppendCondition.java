package org.axonframework.eventsourcing.eventstore;

/**
 * Interface describing the condition of {@link org.axonframework.eventhandling.EventMessage EventMessages} when
 * appending them to an {@link EventStore}.
 *
 * @author Steven van Beelen
 * @author Milan Savic
 * @author Marco Amann
 * @since 5.0.0
 */
public interface AppendCondition {

    /**
     * @return
     */ // TODO I have a hunch we can dump the position here, as the storage engine will take care of the position.
    long position();

    /**
     * @return
     */
    EventCriteria criteria();

    // TODO or make this into a functional interface with a "checkCondition" operation
}
