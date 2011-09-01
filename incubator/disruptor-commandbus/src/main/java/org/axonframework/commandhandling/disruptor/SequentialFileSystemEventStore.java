package org.axonframework.commandhandling.disruptor;

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventstore.EventSerializer;
import org.axonframework.eventstore.SnapshotEventStore;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Allard Buijze
 */
public class SequentialFileSystemEventStore implements SnapshotEventStore {

    private static final String FILE_NAME = "/tmp/trader-event.txt";
    private final ObjectOutputStream os;
    private EventSerializer eventSerializer;

    public SequentialFileSystemEventStore(EventSerializer eventSerializer) {
        this.eventSerializer = eventSerializer;
        try {
            os = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(FILE_NAME),
                                                                 1024 * 1024 * 4));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void appendEvents(String type, DomainEventStream events) {
        try {
            while (events.hasNext()) {
                DomainEvent event = events.next();
                os.writeUTF(type);
                os.writeUTF(event.getAggregateIdentifier().asString());
                byte[] serialized = eventSerializer.serialize(event);
                os.writeInt(serialized.length);
                os.writeUTF(new String(serialized));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DomainEventStream readEvents(String type, AggregateIdentifier identifier) {
        List<DomainEvent> domainEvents = new ArrayList<DomainEvent>();
        ObjectInputStream ois;
        try {
            os.flush();
            ois = new ObjectInputStream(new FileInputStream(FILE_NAME));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            while (true) {
                try {
                    String actualType = ois.readUTF();
                    String actualIdentifier = ois.readUTF();
                    ois.readInt();
                    byte[] serializedEvent = ois.readUTF().getBytes();
                    if (type.equals(actualType) && identifier.asString().equals(actualIdentifier)) {
                        DomainEvent domainEvent = eventSerializer.deserialize(serializedEvent);
                        domainEvents.add(domainEvent);
                    }
                } catch (EOFException e) {
                    break;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } finally {
            try {
                ois.close();
            } catch (IOException e) {
                // whatever
            }
        }
        return new SimpleDomainEventStream(domainEvents);
    }

    @Override
    public void appendSnapshotEvent(String type, DomainEvent snapshotEvent) {
        // ignored for the moment.
    }
}
