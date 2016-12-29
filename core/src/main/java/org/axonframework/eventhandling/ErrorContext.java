package org.axonframework.eventhandling;

import java.util.List;

/**
 * Describes the context of an error.
 */
public class ErrorContext {
    private final String eventProcessor;
    private final Exception error;
    private final List<? extends EventMessage<?>> failedEvents;

    /**
     * @param eventProcessor The name of the event processor that failed to process the given events
     * @param error          The error that was raised during processing
     * @param failedEvents   The list of events that triggered the error
     */
    public ErrorContext(String eventProcessor, Exception error, List<? extends EventMessage<?>> failedEvents) {
        this.eventProcessor = eventProcessor;
        this.error = error;
        this.failedEvents = failedEvents;
    }

    /**
     * Returns the name of the Event Processor where the error occurred.
     *
     * @return the name of the Event Processor where the error occurred
     */
    public String eventProcessor() {
        return eventProcessor;
    }

    /**
     * Returns the error that was raised in the processor
     *
     * @return the error that was raised in the processor
     */
    public Exception error() {
        return error;
    }

    /**
     * The events part of the batch that failed. May be empty if an error occurred outside of the scope of processing a
     * batch (e.g. while preparing the next batch).
     *
     * @return events part of the batch that failed, if any
     */
    public List<? extends EventMessage<?>> failedEvents() {
        return failedEvents;
    }
}
