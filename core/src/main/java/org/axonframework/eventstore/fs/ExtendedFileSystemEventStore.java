package org.axonframework.eventstore.fs;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.eventhandling.replay.ReplayingCluster;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.fs.ListableEventFileResolver.EventFileListConsumer;
import org.axonframework.eventstore.management.Criteria;
import org.axonframework.eventstore.management.CriteriaBuilder;
import org.axonframework.eventstore.management.EventStoreManagement;

/**
 * Extend FileSystemEventStore to implement EventStoreManagement. This is used to allow FileSystemEventStore to be used
 * with {@link ReplayingCluster}
 */
public class ExtendedFileSystemEventStore extends FileSystemEventStore implements EventStoreManagement
{
    protected final ListableEventFileResolver eventFileResolver;
    public ExtendedFileSystemEventStore(ListableEventFileResolver eventFileResolver)
    {
        super(eventFileResolver);
        this.eventFileResolver=eventFileResolver;
    }
    
    @Override
    public CriteriaBuilder newCriteriaBuilder()
    {
        throw new IllegalStateException("Criteria not supported");
    }
    @Override
    public void visitEvents(Criteria criteria, EventVisitor visitor)
    {
        if (criteria!=null) throw new IllegalArgumentException("Criteria not supported");
    }
    @Override
    public void visitEvents(final EventVisitor visitor)
    {
        eventFileResolver.listEvents(new EventFileListConsumer() {
            @Override
            public void consume(String eventType, String identifier)
            {
                DomainEventStream eventStream=readEvents(eventType, identifier);
                while (eventStream.hasNext()) {
                    DomainEventMessage<?> eventMessage=eventStream.next();
                    System.err.println(eventMessage);
                    visitor.doWithEvent(eventMessage);
                }
            }
        });
    }
    
    
    
}
