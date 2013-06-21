package org.axonframework.upcasting;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.SerializedDomainEventMessage;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.UnknownSerializedTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class that optimizes tasks related to upcasting.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public abstract class UpcastUtils {

    private static final Logger logger = LoggerFactory.getLogger(UpcastUtils.class);

    private UpcastUtils() {
    }

    /**
     * Upcasts and deserializes the given <code>entry</code> using the given <code>serializer</code> and
     * <code>upcasterChain</code>. This code is optimized to deserialize the meta-data only once in case it has been
     * used in the upcasting process.
     * <p/>
     * The list of events returned contains lazy deserializing events for optimization purposes. Events represented
     * with
     * unknown classes are ignored, and not returned.
     *
     * @param entry               the entry containing the data of the serialized event
     * @param aggregateIdentifier the original aggregate identifier to use in the deserialized events or
     *                            <code>null</code> to use the deserialized version
     * @param serializer          the serializer to deserialize the event with
     * @param upcasterChain       the chain containing the upcasters to upcast the events with
     * @return a list of upcast and deserialized events
     */
    @SuppressWarnings("unchecked")
    public static List<DomainEventMessage> upcastAndDeserialize(SerializedDomainEventData entry,
                                                                Object aggregateIdentifier,
                                                                Serializer serializer, UpcasterChain upcasterChain) {
        SerializedDomainEventUpcastingContext context = new SerializedDomainEventUpcastingContext(entry, serializer);
        List<SerializedObject> objects = upcasterChain.upcast(entry.getPayload(), context);
        List<DomainEventMessage> events = new ArrayList<DomainEventMessage>(objects.size());
        for (SerializedObject object : objects) {
            try {
                DomainEventMessage<Object> message = new SerializedDomainEventMessage<Object>(
                        new UpcastSerializedDomainEventData(entry,
                                                            firstNonNull(aggregateIdentifier,
                                                                         entry.getAggregateIdentifier()), object),
                        serializer);

                // prevents duplicate deserialization of meta data when it has already been access during upcasting
                if (context.getSerializedMetaData().isDeserialized()) {
                    message = message.withMetaData(context.getSerializedMetaData().getObject());
                }
                events.add(message);
            } catch (UnknownSerializedTypeException e) {
                logger.info("Ignoring event of unknown type {} (rev. {}), as it cannot be resolved to a Class",
                            object.getType().getName(), object.getType().getRevision());
            }
        }
        return events;
    }

    private static Object firstNonNull(Object... instances) {
        for (Object instance : instances) {
            if (instance != null) {
                return instance;
            }
        }
        return null;
    }
}
