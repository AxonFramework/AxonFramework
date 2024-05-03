package org.axonframework.axonserver.connector.event.axon;

import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamConnection;
import org.axonframework.common.Registration;
import org.axonframework.config.Configuration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.SubscribableMessageSource;

import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

public class PersistentStreamMessageSource implements SubscribableMessageSource<EventMessage<?>> {
    private final PersistentStreamConnection persistentStreamConnection;

    public PersistentStreamMessageSource(String name, Configuration configuration, PersistentStreamProperties
            persistentStreamProperties, int batchSize) {
        persistentStreamConnection = new PersistentStreamConnection(name, configuration,
                                        persistentStreamProperties, batchSize);
    }

    @Override
    public Registration subscribe(@Nonnull Consumer<List<? extends EventMessage<?>>> consumer) {
        persistentStreamConnection.open(consumer);
        return () -> {
            persistentStreamConnection.close();
            return true;
        };
    }
}
