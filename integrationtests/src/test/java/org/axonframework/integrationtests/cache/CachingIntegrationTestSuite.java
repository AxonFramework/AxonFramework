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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final int NUMBER_OF_UPDATES = 4096;
    private static final int NUMBER_OF_CONCURRENT_PUBLISHERS = 8;
    private static final String[] SAGA_NAMES = new String[]{"foo", "bar", "baz", "and", "some", "more"};

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
                                          procConfig -> procConfig.usingSubscribingEventProcessors()
                                                                  .registerSaga(CachedSaga.class, sagaConfigurer)
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
        String sagaName = SAGA_NAMES[0];
        String associationValue = sagaName + "-id";
        String associationCacheKey = sagaAssociationCacheKey(associationValue);

        // Construct the saga...
        publish(new CachedSaga.SagaCreatedEvent(associationValue, sagaName));
        // Validate initial cache
        assertTrue(associationsCache.containsKey(associationCacheKey));
        Set<String> associations = associationsCache.get(associationCacheKey);

        String sagaIdentifier = associations.iterator().next();
        assertTrue(sagaCache.containsKey(sagaIdentifier));
        //noinspection unchecked
        CachedSaga cachedSaga = ((SagaStore.Entry<CachedSaga>) sagaCache.get(sagaIdentifier)).saga();
        assertEquals(sagaName, cachedSaga.getName());
        assertTrue(cachedSaga.getState().isEmpty());

        // Bulk update the saga...
        publishBulkUpdatesTo(associationValue, NUMBER_OF_UPDATES);
        // Validate cache again
        assertTrue(associationsCache.containsKey(associationCacheKey));
        associations = associationsCache.get(associationCacheKey);

        sagaIdentifier = associations.iterator().next();
        assertTrue(sagaCache.containsKey(sagaIdentifier));
        //noinspection unchecked
        cachedSaga = ((SagaStore.Entry<CachedSaga>) sagaCache.get(sagaIdentifier)).saga();
        assertEquals(sagaName, cachedSaga.getName());
        assertEquals(NUMBER_OF_UPDATES, cachedSaga.getState().size());

        // Destruct the saga...
        publish(new CachedSaga.SagaEndsEvent(associationValue, true));
        // Validate cache is empty
        assertFalse(associationsCache.containsKey(associationCacheKey));
        assertFalse(sagaCache.containsKey(sagaIdentifier));
    }

    @Test
    void publishingBigEventTransactionsConcurrentlyTowardsCachedSagaWorksWithoutException()
            throws ExecutionException, InterruptedException, TimeoutException {
        String sagaName = SAGA_NAMES[0];
        String associationValue = "some-id";
        String associationCacheKey = sagaAssociationCacheKey(associationValue);
        ExecutorService executor = Executors.newFixedThreadPool(NUMBER_OF_CONCURRENT_PUBLISHERS);

        // Construct the saga...
        publish(new CachedSaga.SagaCreatedEvent(associationValue, sagaName));
        // Validate initial cache
        assertTrue(associationsCache.containsKey(associationCacheKey));
        Set<String> associations = associationsCache.get(associationCacheKey);

        String sagaIdentifier = associations.iterator().next();
        assertTrue(sagaCache.containsKey(sagaIdentifier));
        //noinspection unchecked
        CachedSaga cachedSaga = ((SagaStore.Entry<CachedSaga>) sagaCache.get(sagaIdentifier)).saga();
        assertEquals(sagaName, cachedSaga.getName());
        assertTrue(cachedSaga.getState().isEmpty());

        // Concurrent bulk update the saga...
        IntStream.range(0, NUMBER_OF_CONCURRENT_PUBLISHERS)
                 .mapToObj(i -> CompletableFuture.runAsync(
                         () -> publishBulkUpdatesTo(associationValue, NUMBER_OF_UPDATES), executor
                 ))
                 .reduce(CompletableFuture::allOf)
                 .orElse(CompletableFuture.completedFuture(null))
                 .get(5, TimeUnit.SECONDS);

        // Validate cache again
        assertTrue(associationsCache.containsKey(associationCacheKey));
        associations = associationsCache.get(associationCacheKey);

        sagaIdentifier = associations.iterator().next();
        assertTrue(sagaCache.containsKey(sagaIdentifier));
        //noinspection unchecked
        cachedSaga = ((SagaStore.Entry<CachedSaga>) sagaCache.get(sagaIdentifier)).saga();
        assertEquals(sagaName, cachedSaga.getName());
        assertEquals(NUMBER_OF_UPDATES * NUMBER_OF_CONCURRENT_PUBLISHERS, cachedSaga.getState().size());

        // Destruct the saga...
        publish(new CachedSaga.SagaEndsEvent(associationValue, true));
        // Validate cache is empty
        assertFalse(associationsCache.containsKey(associationCacheKey));
        assertFalse(sagaCache.containsKey(sagaIdentifier));
    }

    @Test
    void publishingBigEventTransactionTowardsSeveralCachedSagasWorksWithoutException()
            throws ExecutionException, InterruptedException, TimeoutException {
        Map<String, Set<String>> associationReferences = new HashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(SAGA_NAMES.length);

        // Construct the sagas...
        for (String sagaName : SAGA_NAMES) {
            String associationValue = sagaName + "-id";
            String associationCacheKey = sagaAssociationCacheKey(associationValue);

            publish(new CachedSaga.SagaCreatedEvent(associationValue, sagaName));
            // Validate initial cache
            assertTrue(associationsCache.containsKey(associationCacheKey));
            associationReferences.put(associationCacheKey, associationsCache.get(associationCacheKey));

            String sagaIdentifier = (associationReferences.get(associationCacheKey)).iterator().next();
            assertTrue(sagaCache.containsKey(sagaIdentifier));

            SagaStore.Entry<CachedSaga> sagaEntry = sagaCache.get(sagaIdentifier);
            CachedSaga cachedSaga = sagaEntry.saga();
            assertEquals(sagaName, cachedSaga.getName());
            assertTrue(cachedSaga.getState().isEmpty());
        }

        // Bulk update the sagas...
        Arrays.stream(SAGA_NAMES)
              .map(name -> CompletableFuture.runAsync(
                      () -> publishBulkUpdatesTo(name + "-id", NUMBER_OF_UPDATES), executor
              ))
              .reduce(CompletableFuture::allOf)
              .orElse(CompletableFuture.completedFuture(null))
              .get(5, TimeUnit.SECONDS);
        // Validate caches again
        for (String sagaName : SAGA_NAMES) {
            String associationValue = sagaName + "-id";
            String associationCacheKey = sagaAssociationCacheKey(associationValue);

            assertTrue(associationsCache.containsKey(associationCacheKey));
            associationReferences.put(associationCacheKey, associationsCache.get(associationCacheKey));

            String sagaIdentifier = (associationReferences.get(associationCacheKey)).iterator().next();
            SagaStore.Entry<CachedSaga> sagaEntry = sagaCache.get(sagaIdentifier);
            // The saga cache may have been cleared already when doing bulk updates, which is a fair scenario.
            // Hence, only validate the entry if it's still present in the Cache.
            if (sagaEntry != null) {
                CachedSaga cachedSaga = sagaEntry.saga();
                assertEquals(sagaName, cachedSaga.getName());
                assertEquals(NUMBER_OF_UPDATES, cachedSaga.getState().size(), sagaName);
            }
        }

        // Destruct the sagas...
        for (String sagaName : SAGA_NAMES) {
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
        Map<String, Set<String>> associationReferences = new HashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(SAGA_NAMES.length * NUMBER_OF_CONCURRENT_PUBLISHERS);

        // Construct the sagas...
        for (String sagaName : SAGA_NAMES) {
            String associationValue = sagaName + "-id";
            String associationCacheKey = sagaAssociationCacheKey(associationValue);

            publish(new CachedSaga.SagaCreatedEvent(associationValue, sagaName));
            // Validate initial cache
            assertTrue(associationsCache.containsKey(associationCacheKey));
            associationReferences.put(associationCacheKey, associationsCache.get(associationCacheKey));

            String sagaIdentifier = (associationReferences.get(associationCacheKey)).iterator().next();
            assertTrue(sagaCache.containsKey(sagaIdentifier));

            SagaStore.Entry<CachedSaga> sagaEntry = sagaCache.get(sagaIdentifier);
            CachedSaga cachedSaga = sagaEntry.saga();
            assertEquals(sagaName, cachedSaga.getName());
            assertTrue(cachedSaga.getState().isEmpty());
        }

        // Bulk update the sagas...
        IntStream.range(0, SAGA_NAMES.length * NUMBER_OF_CONCURRENT_PUBLISHERS)
                 .mapToObj(i -> CompletableFuture.runAsync(
                         () -> publishBulkUpdatesTo(SAGA_NAMES[i % SAGA_NAMES.length] + "-id", NUMBER_OF_UPDATES),
                         executor
                 ))
                 .reduce(CompletableFuture::allOf)
                 .orElse(CompletableFuture.completedFuture(null))
                 .get(5, TimeUnit.SECONDS);
        // Validate caches again
        for (String sagaName : SAGA_NAMES) {
            String associationValue = sagaName + "-id";
            String associationCacheKey = sagaAssociationCacheKey(associationValue);

            assertTrue(associationsCache.containsKey(associationCacheKey));
            associationReferences.put(associationCacheKey, associationsCache.get(associationCacheKey));

            String sagaIdentifier = (associationReferences.get(associationCacheKey)).iterator().next();
            SagaStore.Entry<CachedSaga> sagaEntry = sagaCache.get(sagaIdentifier);
            // The saga cache may have been cleared already when doing bulk updates, which is a fair scenario.
            // Hence, only validate the entry if it's still present in the Cache.
            if (sagaEntry != null) {
                CachedSaga cachedSaga = sagaEntry.saga();
                assertEquals(sagaName, cachedSaga.getName());
                assertEquals(NUMBER_OF_UPDATES * NUMBER_OF_CONCURRENT_PUBLISHERS,
                             cachedSaga.getState().size(),
                             sagaName);
            }
        }

        // Destruct the sagas...
        for (String sagaName : SAGA_NAMES) {
            String associationValue = sagaName + "-id";
            String associationCacheKey = sagaAssociationCacheKey(associationValue);

            publish(new CachedSaga.SagaEndsEvent(associationValue, true));
            // Validate cache is empty
            assertFalse(associationsCache.containsKey(associationCacheKey));
        }
    }

    private void publishBulkUpdatesTo(String sagaId,
                                      @SuppressWarnings("SameParameterValue") int amount) {
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
