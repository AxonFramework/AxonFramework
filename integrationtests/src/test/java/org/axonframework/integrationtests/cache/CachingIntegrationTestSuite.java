package org.axonframework.integrationtests.cache;

import org.axonframework.common.caching.Cache;
import org.axonframework.config.Configuration;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.config.SagaConfigurer;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.modelling.saga.repository.CachingSagaStore;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.inmemory.InMemorySagaStore;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Abstract integration test suite to validate the provided {@link org.axonframework.common.caching.Cache}
 * implementations to work as intended under various stress scenarios.
 *
 * @author Steven van Beelen
 */
public abstract class CachingIntegrationTestSuite {

    // This ensures we do not wire Axon Server components
    private static final boolean DO_NOT_AUTO_LOCATE_CONFIGURER_MODULES = false;

    protected Configuration config;

    private Cache sagaCache;
    private Cache associationsCache;

    @BeforeEach
    void setUp() {
        EventStore eventStore = spy(EmbeddedEventStore.builder()
                                                      .storageEngine(new InMemoryEventStorageEngine())
                                                      .build());
        sagaCache = buildCache("saga");
        associationsCache = buildCache("associations");

        Consumer<SagaConfigurer<CachedSaga>> sagaConfigurer =
                config -> config.configureSagaStore(c -> CachingSagaStore.builder()
                                                                         .delegateSagaStore(new InMemorySagaStore())
                                                                         .sagaCache(sagaCache)
                                                                         .associationsCache(associationsCache)
                                                                         .build());

        config = DefaultConfigurer.defaultConfiguration(DO_NOT_AUTO_LOCATE_CONFIGURER_MODULES)
                                  .configureEventStore(c -> eventStore)
                                  .eventProcessing(
                                          procConfigurer -> procConfigurer.usingSubscribingEventProcessors()
                                                                          .registerSaga(
                                                                                  CachedSaga.class,
                                                                                  sagaConfigurer
                                                                          )
                                  )
                                  .start();
    }

    /**
     * Construct a {@link Cache} implementation used during testing.
     *
     * @param name The name to give to the {@link Cache} under construction.
     * @return The constructed {@link Cache} instance.
     */
    public abstract Cache buildCache(String name);

    @Test
    void publishingBigEventTransactionTowardsCachedSagaWorksWithoutException() {
        String sagaName = "foo";
        String associationValue = sagaName + "-id";
        String associationCacheKey = sagaAssociationCacheKey(associationValue);
        int numberOfUpdates = 4096;

        // Construct the saga...
        publish(new CachedSaga.SagaCreatedEvent(associationValue, sagaName));
        // Validate initial cache
        assertTrue(associationsCache.containsKey(associationCacheKey));
        //noinspection unchecked
        String sagaIdentifier = ((Set<String>) associationsCache.get(associationCacheKey)).iterator().next();
        assertTrue(sagaCache.containsKey(sagaIdentifier));
        //noinspection unchecked
        CachedSaga cachedSaga = ((SagaStore.Entry<CachedSaga>) sagaCache.get(sagaIdentifier)).saga();
        assertEquals(sagaName, cachedSaga.getName());
        assertTrue(cachedSaga.getState().isEmpty());

        // Clear out the cache
        associationsCache.removeAll();
        sagaCache.removeAll();

        // Bulk update the saga...
        publishBulkUpdatesTo(associationValue, numberOfUpdates);
        // Validate cache again
        assertTrue(associationsCache.containsKey(associationCacheKey));
        //noinspection unchecked
        sagaIdentifier = ((Set<String>) associationsCache.get(associationCacheKey)).iterator().next();
        assertTrue(sagaCache.containsKey(sagaIdentifier));
        //noinspection unchecked
        cachedSaga = ((SagaStore.Entry<CachedSaga>) sagaCache.get(sagaIdentifier)).saga();
        assertEquals(sagaName, cachedSaga.getName());
        assertEquals(numberOfUpdates, cachedSaga.getState().size());

        // Clear out the cache
        associationsCache.removeAll();
        sagaCache.removeAll();

        // Destruct the saga...
        publish(new CachedSaga.SagaEndsEvent(associationValue, true));
        // Validate cache is empty
        assertFalse(associationsCache.containsKey(associationCacheKey));
        assertFalse(sagaCache.containsKey(sagaIdentifier));
    }

    @Test
    void publishingBigEventTransactionsConcurrentlyTowardsCachedSagaWorksWithoutException()
            throws ExecutionException, InterruptedException, TimeoutException {
        String sagaName = "yeet";
        String associationValue = "some-id";
        String associationCacheKey = sagaAssociationCacheKey(associationValue);
        int numberOfUpdates = 4096;
        int numberOfConcurrentPublishers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfConcurrentPublishers);

        // Construct the saga...
        publish(new CachedSaga.SagaCreatedEvent(associationValue, sagaName));
        // Validate initial cache
        assertTrue(associationsCache.containsKey(associationCacheKey));
        //noinspection unchecked
        String sagaIdentifier = ((Set<String>) associationsCache.get(associationCacheKey)).iterator().next();
        assertTrue(sagaCache.containsKey(sagaIdentifier));
        //noinspection unchecked
        CachedSaga cachedSaga = ((SagaStore.Entry<CachedSaga>) sagaCache.get(sagaIdentifier)).saga();
        assertEquals(sagaName, cachedSaga.getName());
        assertTrue(cachedSaga.getState().isEmpty());

        // Clear out the cache
        associationsCache.removeAll();
        sagaCache.removeAll();

        // Concurrent bulk update the saga...
        IntStream.range(0, numberOfConcurrentPublishers)
                 .mapToObj(i -> CompletableFuture.runAsync(
                         () -> publishBulkUpdatesTo(associationValue, numberOfUpdates), executor
                 ))
                 .reduce(CompletableFuture::allOf)
                 .orElse(CompletableFuture.completedFuture(null))
                 .get(5, TimeUnit.SECONDS);

        // Validate cache again
        assertTrue(associationsCache.containsKey(associationCacheKey));
        //noinspection unchecked
        sagaIdentifier = ((Set<String>) associationsCache.get(associationCacheKey)).iterator().next();
        assertTrue(sagaCache.containsKey(sagaIdentifier));
        //noinspection unchecked
        cachedSaga = ((SagaStore.Entry<CachedSaga>) sagaCache.get(sagaIdentifier)).saga();
        assertEquals(sagaName, cachedSaga.getName());
        assertEquals(numberOfUpdates * numberOfConcurrentPublishers, cachedSaga.getState().size());

        // Clear out the cache
        associationsCache.removeAll();
        sagaCache.removeAll();

        // Destruct the saga...
        publish(new CachedSaga.SagaEndsEvent(associationValue, true));
        // Validate cache is empty
        assertFalse(associationsCache.containsKey(associationCacheKey));
        assertFalse(sagaCache.containsKey(sagaIdentifier));
    }

    @Test
    void publishingBigEventTransactionTowardsSeveralCachedSagasWorksWithoutException()
            throws ExecutionException, InterruptedException, TimeoutException {
        String[] sagaNames = new String[]{"foo", "bar", "baz", "and", "some", "more"};
        int numberOfUpdates = 4096;
        ExecutorService executor = Executors.newFixedThreadPool(sagaNames.length);

        // Construct the sagas...
        for (String sagaName : sagaNames) {
            String associationValue = sagaName + "-id";
            String associationCacheKey = sagaAssociationCacheKey(associationValue);

            publish(new CachedSaga.SagaCreatedEvent(associationValue, sagaName));
            // Validate initial cache
            assertTrue(associationsCache.containsKey(associationCacheKey));
            //noinspection unchecked
            String sagaIdentifier = ((Set<String>) associationsCache.get(associationCacheKey)).iterator().next();
            assertTrue(sagaCache.containsKey(sagaIdentifier));
            //noinspection unchecked
            CachedSaga cachedSaga = ((SagaStore.Entry<CachedSaga>) sagaCache.get(sagaIdentifier)).saga();
            assertEquals(sagaName, cachedSaga.getName());
            assertTrue(cachedSaga.getState().isEmpty());
        }

        // Clear out the cache
        associationsCache.removeAll();
        sagaCache.removeAll();

        // Bulk update the sagas...
        Arrays.stream(sagaNames)
              .map(name -> CompletableFuture.runAsync(
                      () -> publishBulkUpdatesTo(name + "-id", numberOfUpdates), executor
              ))
              .reduce(CompletableFuture::allOf)
              .orElse(CompletableFuture.completedFuture(null))
              .get(5, TimeUnit.SECONDS);
        // Validate caches again
        for (String sagaName : sagaNames) {
            String associationValue = sagaName + "-id";
            String associationCacheKey = sagaAssociationCacheKey(associationValue);

            assertTrue(associationsCache.containsKey(associationCacheKey));
            //noinspection unchecked
            String sagaIdentifier = ((Set<String>) associationsCache.get(associationCacheKey)).iterator().next();
            assertTrue(sagaCache.containsKey(sagaIdentifier));
            //noinspection unchecked
            CachedSaga cachedSaga = ((SagaStore.Entry<CachedSaga>) sagaCache.get(sagaIdentifier)).saga();
            assertEquals(sagaName, cachedSaga.getName());
            assertEquals(numberOfUpdates, cachedSaga.getState().size(), sagaName);
        }

        // Clear out the cache
        associationsCache.removeAll();
        sagaCache.removeAll();

        // Destruct the sagas...
        for (String sagaName : sagaNames) {
            String associationValue = sagaName + "-id";
            String associationCacheKey = sagaAssociationCacheKey(associationValue);

            publish(new CachedSaga.SagaEndsEvent(associationValue, true));
            // Validate cache is empty
            assertFalse(associationsCache.containsKey(associationCacheKey));
        }
    }

    @Test
    void publishingBigEventTransactionsConcurrentlyTowardsSeveralCachedSagasWorksWithoutException()
            throws ExecutionException, InterruptedException, TimeoutException {
        String[] sagaNames = new String[]{"foo", "bar", "baz", "and", "some", "more"};
        int numberOfUpdates = 4096;
        int numberOfConcurrentPublishers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(sagaNames.length * numberOfConcurrentPublishers);

        // Construct the sagas...
        for (String sagaName : sagaNames) {
            String associationValue = sagaName + "-id";
            String associationCacheKey = sagaAssociationCacheKey(associationValue);

            publish(new CachedSaga.SagaCreatedEvent(associationValue, sagaName));
            // Validate initial cache
            assertTrue(associationsCache.containsKey(associationCacheKey));
            //noinspection unchecked
            String sagaIdentifier = ((Set<String>) associationsCache.get(associationCacheKey)).iterator().next();
            assertTrue(sagaCache.containsKey(sagaIdentifier));
            //noinspection unchecked
            CachedSaga cachedSaga = ((SagaStore.Entry<CachedSaga>) sagaCache.get(sagaIdentifier)).saga();
            assertEquals(sagaName, cachedSaga.getName());
            assertTrue(cachedSaga.getState().isEmpty());
        }

        // Clear out the cache
        associationsCache.removeAll();
        sagaCache.removeAll();

        // Bulk update the sagas...
        IntStream.range(0, sagaNames.length * numberOfConcurrentPublishers)
                 .mapToObj(i -> CompletableFuture.runAsync(
                         () -> publishBulkUpdatesTo(sagaNames[i % sagaNames.length] + "-id", numberOfUpdates), executor
                 ))
                 .reduce(CompletableFuture::allOf)
                 .orElse(CompletableFuture.completedFuture(null))
                 .get(5, TimeUnit.SECONDS);
        // Validate caches again
        for (String sagaName : sagaNames) {
            String associationValue = sagaName + "-id";
            String associationCacheKey = sagaAssociationCacheKey(associationValue);

            assertTrue(associationsCache.containsKey(associationCacheKey));
            //noinspection unchecked
            String sagaIdentifier = ((Set<String>) associationsCache.get(associationCacheKey)).iterator().next();
            assertTrue(sagaCache.containsKey(sagaIdentifier));
            //noinspection unchecked
            CachedSaga cachedSaga = ((SagaStore.Entry<CachedSaga>) sagaCache.get(sagaIdentifier)).saga();
            assertEquals(sagaName, cachedSaga.getName());
            assertEquals(numberOfUpdates * numberOfConcurrentPublishers, cachedSaga.getState().size(), sagaName);
        }

        // Clear out the cache
        associationsCache.removeAll();
        sagaCache.removeAll();

        // Destruct the sagas...
        for (String sagaName : sagaNames) {
            String associationValue = sagaName + "-id";
            String associationCacheKey = sagaAssociationCacheKey(associationValue);

            publish(new CachedSaga.SagaEndsEvent(associationValue, true));
            // Validate cache is empty
            assertFalse(associationsCache.containsKey(associationCacheKey));
        }
    }

    private void publishBulkUpdatesTo(String sagaId, int amount) {
        Object[] updateEvents = new Object[amount];
        for (int i = 0; i < amount; i++) {
            updateEvents[i] = new CachedSaga.VeryImportantEvent(sagaId, i);
        }
        publish(updateEvents);
    }

    private void publish(Object... events) {
        List<EventMessage<?>> eventMessages = new ArrayList<>();
        for (Object event : events) {
            eventMessages.add(GenericEventMessage.asEventMessage(event));
        }
        config.eventStore().publish(eventMessages);
    }

    /**
     * This method is based on the private {@code CachingSagaStore#cacheKey(AssociationValue, Class<?>)} method.
     *
     * @param sagaId The association key within the event.
     * @return The caching key used for the association values by a {@link CachingSagaStore}.
     */
    private static String sagaAssociationCacheKey(String sagaId) {
        return CachedSaga.class.getName() + "/id=" + sagaId;
    }
}
