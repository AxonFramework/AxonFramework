package org.axonframework.eventstore.fs;


/**
 * Extension to EventFileResolver to allow to iterate through all aggregates
 */
public interface ListableEventFileResolver extends EventFileResolver
{
    public void listEvents(EventFileListConsumer consumer);
    
    public static interface EventFileListConsumer
    {
        public void consume(String eventType, String identifier);
    }
}
