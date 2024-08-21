package org.axonframework.eventsourcing.eventstore;

/**
 * Test class validating the {@link AsyncEmbeddedEventStore} together with the {@link AsyncInMemoryEventStorageEngine}.
 *
 * @author Steven van Beelen
 */
class InMemoryAsyncEmbeddedEventStoreTest extends AsyncEmbeddedEventStoreTest<AsyncInMemoryEventStorageEngine> {

    @Override
    protected AsyncInMemoryEventStorageEngine buildStorageEngine() {
        return new AsyncInMemoryEventStorageEngine();
    }
}
