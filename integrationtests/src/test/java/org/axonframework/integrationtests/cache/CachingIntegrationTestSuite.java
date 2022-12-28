/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.integrationtests.cache;

import org.axonframework.common.caching.Cache;
import org.axonframework.config.Configuration;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.config.SagaConfigurer;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.StreamingEventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.modelling.saga.repository.CachingSagaStore;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.inmemory.InMemorySagaStore;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Abstract integration test suite to validate the provided {@link org.axonframework.common.caching.Cache}
 * implementations to work as intended under various stress scenarios.
 * <p>
 * Uses a custom {@link org.axonframework.common.caching.Cache.EntryListener} to validate whether the {@link Cache}
 * under test is hit accordingly. Doing so provides additional certainty that although the {@code Cache} may have
 * adjusted under the hood, the expected invocation have been made.
 *
 * @author Steven van Beelen
 */
public abstract class CachingIntegrationTestSuite {

    // This ensures we do not wire Axon Server components
    private static final boolean DO_NOT_AUTO_LOCATE_CONFIGURER_MODULES = false;
    private static final int NUMBER_OF_UPDATES = 4096;
    private static final int NUMBER_OF_CONCURRENT_PUBLISHERS = 8;
    private static final String[] SAGA_NAMES = new String[]{"foo", "bar", "baz", "and", "some", "more"};
    private static final int NUMBER_OF_ASSOCIATIONS = 42;

    private static final Duration DEFAULT_DELAY = Duration.ofMillis(25);
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);
    private static final Duration TWO_SECONDS = Duration.ofSeconds(2);
    private static final Duration FOUR_SECONDS = Duration.ofSeconds(4);
    private static final Duration EIGHT_SECONDS = Duration.ofSeconds(8);

    protected Configuration config;
    private StreamingEventProcessor sagaProcessor;

    private EntryListenerValidator<SagaStore.Entry<CachedSaga>> sagaCacheListener;
    private EntryListenerValidator<Set<String>> associationsCacheListener;

    @BeforeEach
    void setUp() {
        Cache sagaCache = buildCache("saga");
        sagaCacheListener = new EntryListenerValidator<>(ListenerType.SAGA);
        //noinspection resource
        sagaCache.registerCacheEntryListener(sagaCacheListener);

        Cache associationsCache = buildCache("associations");
        associationsCacheListener = new EntryListenerValidator<>(ListenerType.ASSOCIATIONS);
        //noinspection resource
        associationsCache.registerCacheEntryListener(associationsCacheListener);

        Consumer<SagaConfigurer<CachedSaga>> sagaConfigurer =
                config -> config.configureSagaStore(c -> CachingSagaStore.builder()
                                                                         .delegateSagaStore(new InMemorySagaStore())
                                                                         .sagaCache(sagaCache)
                                                                         .associationsCache(associationsCache)
                                                                         .build());

        TrackingEventProcessorConfiguration tepConfig =
                TrackingEventProcessorConfiguration.forParallelProcessing(4)
                                                   .andEventAvailabilityTimeout(10, TimeUnit.MILLISECONDS);
        config = DefaultConfigurer.defaultConfiguration(DO_NOT_AUTO_LOCATE_CONFIGURER_MODULES)
                                  .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                  .eventProcessing(
                                          procConfig -> procConfig
                                                  .usingTrackingEventProcessors()
                                                  .registerTrackingEventProcessorConfiguration(
                                                          "CachedSagaProcessor", c -> tepConfig
                                                  )
                                                  .registerSaga(CachedSaga.class, sagaConfigurer)
                                  )
                                  .start();
        sagaProcessor = config.eventProcessingConfiguration()
                              .eventProcessor("CachedSagaProcessor", StreamingEventProcessor.class)
                              .orElseThrow(() -> new IllegalStateException("CachedSagaProcessor is not present"));
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
        int createEvents = 1;
        int deleteEvents = 1;
        String sagaName = SAGA_NAMES[0];
        String associationValue = sagaName + "-id";
        String associationCacheKey = sagaAssociationCacheKey(associationValue);

        // Construct the saga...
        publish(new CachedSaga.SagaCreatedEvent(associationValue, sagaName, NUMBER_OF_ASSOCIATIONS));
        await().pollDelay(DEFAULT_DELAY)
               .atMost(ONE_SECOND)
               .until(() -> handledEventsUpTo(createEvents));

        // Validate initial association cache
        Optional<Set<String>> optionalAssociations = associationsCacheListener.get(associationCacheKey);
        assertTrue(optionalAssociations.isPresent());
        Optional<String> optionalAssociationValue = optionalAssociations.get().stream().findFirst();
        assertTrue(optionalAssociationValue.isPresent());
        String sagaIdentifier = optionalAssociationValue.get();
        // Validate initial saga cache
        Optional<SagaStore.Entry<CachedSaga>> optionalCachedSaga = sagaCacheListener.get(sagaIdentifier);
        assertTrue(optionalCachedSaga.isPresent());
        CachedSaga cachedSaga = optionalCachedSaga.get().saga();
        assertEquals(sagaName, cachedSaga.getName());
        assertTrue(cachedSaga.getState().isEmpty());

        // Bulk update the saga...
        publishBulkUpdatesTo(associationValue, NUMBER_OF_UPDATES);
        await().pollDelay(DEFAULT_DELAY)
               .atMost(TWO_SECONDS)
               .until(() -> handledEventsUpTo(createEvents + NUMBER_OF_UPDATES));

        // Validate caches again
        optionalCachedSaga = sagaCacheListener.get(sagaIdentifier);
        assertTrue(optionalCachedSaga.isPresent());
        cachedSaga = optionalCachedSaga.get().saga();
        assertEquals(sagaName, cachedSaga.getName());
        assertEquals(NUMBER_OF_UPDATES, cachedSaga.getState().size());

        // Destruct the saga...
        publish(new CachedSaga.SagaEndsEvent(associationValue, true));
        await().pollDelay(DEFAULT_DELAY)
               .atMost(ONE_SECOND)
               .until(() -> handledEventsUpTo(createEvents + NUMBER_OF_UPDATES + deleteEvents));

        // Validate caches are empty
        assertTrue(associationsCacheListener.isRemoved(associationCacheKey));
        assertTrue(sagaCacheListener.isRemoved(sagaIdentifier));
    }

    @Test
    void publishingBigEventTransactionsConcurrentlyTowardsCachedSagaWorksWithoutException()
            throws ExecutionException, InterruptedException, TimeoutException {
        int createEvents = 1;
        int deleteEvents = 1;
        String sagaName = SAGA_NAMES[0];
        String associationValue = "some-id";
        String associationCacheKey = sagaAssociationCacheKey(associationValue);
        ExecutorService executor = Executors.newFixedThreadPool(NUMBER_OF_CONCURRENT_PUBLISHERS);

        // Construct the saga...
        publish(new CachedSaga.SagaCreatedEvent(associationValue, sagaName, NUMBER_OF_ASSOCIATIONS));
        await().pollDelay(DEFAULT_DELAY)
               .atMost(TWO_SECONDS)
               .until(() -> handledEventsUpTo(createEvents));

        // Validate initial association cache
        Optional<Set<String>> optionalAssociations = associationsCacheListener.get(associationCacheKey);
        assertTrue(optionalAssociations.isPresent());
        Optional<String> optionalAssociationValue = optionalAssociations.get().stream().findFirst();
        assertTrue(optionalAssociationValue.isPresent());
        String sagaIdentifier = optionalAssociationValue.get();
        // Validate initial saga cache
        Optional<SagaStore.Entry<CachedSaga>> optionalCachedSaga = sagaCacheListener.get(sagaIdentifier);
        assertTrue(optionalCachedSaga.isPresent());
        CachedSaga cachedSaga = optionalCachedSaga.get().saga();
        assertEquals(sagaName, cachedSaga.getName());
        assertTrue(cachedSaga.getState().isEmpty());

        // Concurrent bulk update the saga...
        IntStream.range(0, NUMBER_OF_CONCURRENT_PUBLISHERS)
                 .mapToObj(i -> CompletableFuture.runAsync(
                         () -> publishBulkUpdatesTo(associationValue, NUMBER_OF_UPDATES), executor
                 ))
                 .reduce(CompletableFuture::allOf)
                 .orElse(CompletableFuture.completedFuture(null))
                 .get(15, TimeUnit.SECONDS);
        await().pollDelay(DEFAULT_DELAY)
               .atMost(EIGHT_SECONDS)
               .until(() -> handledEventsUpTo(createEvents + (NUMBER_OF_UPDATES * NUMBER_OF_CONCURRENT_PUBLISHERS)));

        // Validate caches again
        optionalCachedSaga = sagaCacheListener.get(sagaIdentifier);
        assertTrue(optionalCachedSaga.isPresent());
        cachedSaga = optionalCachedSaga.get().saga();
        assertEquals(sagaName, cachedSaga.getName());
        assertEquals(NUMBER_OF_UPDATES * NUMBER_OF_CONCURRENT_PUBLISHERS, cachedSaga.getState().size());

        // Destruct the saga...
        publish(new CachedSaga.SagaEndsEvent(associationValue, true));
        await().pollDelay(DEFAULT_DELAY)
               .atMost(TWO_SECONDS)
               .until(() -> handledEventsUpTo(
                       createEvents + (NUMBER_OF_UPDATES * NUMBER_OF_CONCURRENT_PUBLISHERS) + deleteEvents
               ));

        // Validate caches are empty
        assertTrue(associationsCacheListener.isRemoved(associationCacheKey));
        assertTrue(sagaCacheListener.isRemoved(sagaIdentifier));
    }

    @Test
    void publishingBigEventTransactionTowardsSeveralCachedSagasWorksWithoutException()
            throws ExecutionException, InterruptedException, TimeoutException {
        int createEvents = SAGA_NAMES.length;
        int deleteEvents = SAGA_NAMES.length;
        ExecutorService executor = Executors.newFixedThreadPool(SAGA_NAMES.length);

        // Construct the sagas...
        for (String sagaName : SAGA_NAMES) {
            publish(new CachedSaga.SagaCreatedEvent(sagaName + "-id", sagaName, NUMBER_OF_ASSOCIATIONS));
        }
        await().pollDelay(DEFAULT_DELAY)
               .atMost(TWO_SECONDS)
               .until(() -> handledEventsUpTo(createEvents));

        // Validate initial caches
        for (String sagaName : SAGA_NAMES) {
            String associationValue = sagaName + "-id";
            String associationCacheKey = sagaAssociationCacheKey(associationValue);
            // Validate initial association cache
            Optional<Set<String>> optionalAssociations = associationsCacheListener.get(associationCacheKey);
            assertTrue(optionalAssociations.isPresent());
            Optional<String> optionalAssociationValue = optionalAssociations.get().stream().findFirst();
            assertTrue(optionalAssociationValue.isPresent());
            String sagaIdentifier = optionalAssociationValue.get();
            // Validate initial saga cache
            Optional<SagaStore.Entry<CachedSaga>> optionalCachedSaga = sagaCacheListener.get(sagaIdentifier);
            assertTrue(optionalCachedSaga.isPresent());
            CachedSaga cachedSaga = optionalCachedSaga.get().saga();
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
              .get(15, TimeUnit.SECONDS);
        await().pollDelay(DEFAULT_DELAY)
               .atMost(EIGHT_SECONDS)
               .until(() -> handledEventsUpTo(createEvents + (NUMBER_OF_UPDATES * SAGA_NAMES.length)));

        // Validate caches again
        for (String sagaName : SAGA_NAMES) {
            String associationValue = sagaName + "-id";
            String associationCacheKey = sagaAssociationCacheKey(associationValue);

            Optional<Set<String>> optionalAssociations = associationsCacheListener.get(associationCacheKey);
            assertTrue(optionalAssociations.isPresent());
            Optional<String> optionalAssociationValue = optionalAssociations.get().stream().findFirst();
            assertTrue(optionalAssociationValue.isPresent());
            String sagaIdentifier = optionalAssociationValue.get();

            Optional<SagaStore.Entry<CachedSaga>> optionalCachedSaga = sagaCacheListener.get(sagaIdentifier);
            assertTrue(optionalCachedSaga.isPresent());
            CachedSaga cachedSaga = optionalCachedSaga.get().saga();
            assertEquals(sagaName, cachedSaga.getName());
            assertEquals(NUMBER_OF_UPDATES, cachedSaga.getState().size());
        }

        // Destruct the sagas...
        for (String sagaName : SAGA_NAMES) {
            publish(new CachedSaga.SagaEndsEvent(sagaName + "-id", true));
        }
        await().pollDelay(DEFAULT_DELAY)
               .atMost(TWO_SECONDS)
               .until(() -> handledEventsUpTo(
                       createEvents + (NUMBER_OF_UPDATES * SAGA_NAMES.length) + deleteEvents
               ));

        // Validate association cache is empty
        for (String sagaName : SAGA_NAMES) {
            assertTrue(associationsCacheListener.isRemoved(sagaAssociationCacheKey(sagaName + "-id")));
        }
    }

    @Test
    void publishingBigEventTransactionsConcurrentlyTowardsSeveralCachedSagasWorksWithoutException()
            throws ExecutionException, InterruptedException, TimeoutException {
        int createEvents = SAGA_NAMES.length;
        int deleteEvents = SAGA_NAMES.length;
        ExecutorService executor = Executors.newFixedThreadPool(SAGA_NAMES.length * NUMBER_OF_CONCURRENT_PUBLISHERS);

        // Construct the sagas...
        for (String sagaName : SAGA_NAMES) {
            publish(new CachedSaga.SagaCreatedEvent(sagaName + "-id", sagaName, NUMBER_OF_ASSOCIATIONS));
        }
        await().pollDelay(DEFAULT_DELAY)
               .atMost(TWO_SECONDS)
               .until(() -> handledEventsUpTo(createEvents));

        // Validate initial cache
        for (String sagaName : SAGA_NAMES) {
            String associationValue = sagaName + "-id";
            String associationCacheKey = sagaAssociationCacheKey(associationValue);

            // Validate initial association cache
            Optional<Set<String>> optionalAssociations = associationsCacheListener.get(associationCacheKey);
            assertTrue(optionalAssociations.isPresent());
            Optional<String> optionalAssociationValue = optionalAssociations.get().stream().findFirst();
            assertTrue(optionalAssociationValue.isPresent());
            String sagaIdentifier = optionalAssociationValue.get();
            // Validate initial saga cache
            Optional<SagaStore.Entry<CachedSaga>> optionalCachedSaga = sagaCacheListener.get(sagaIdentifier);
            assertTrue(optionalCachedSaga.isPresent());
            CachedSaga cachedSaga = optionalCachedSaga.get().saga();
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
                 .get(15, TimeUnit.SECONDS);
        await().pollDelay(DEFAULT_DELAY)
               .atMost(Duration.ofSeconds(20))
               .until(() -> handledEventsUpTo(
                       createEvents + (NUMBER_OF_UPDATES * (SAGA_NAMES.length * NUMBER_OF_CONCURRENT_PUBLISHERS))
               ));

        // Validate caches again
        for (String sagaName : SAGA_NAMES) {
            String associationValue = sagaName + "-id";
            String associationCacheKey = sagaAssociationCacheKey(associationValue);

            Optional<Set<String>> optionalAssociations = associationsCacheListener.get(associationCacheKey);
            assertTrue(optionalAssociations.isPresent());
            Optional<String> optionalAssociationValue = optionalAssociations.get().stream().findFirst();
            assertTrue(optionalAssociationValue.isPresent());
            String sagaIdentifier = optionalAssociationValue.get();

            Optional<SagaStore.Entry<CachedSaga>> optionalCachedSaga = sagaCacheListener.get(sagaIdentifier);
            assertTrue(optionalCachedSaga.isPresent());
            CachedSaga cachedSaga = optionalCachedSaga.get().saga();
            assertEquals(sagaName, cachedSaga.getName());
            assertEquals(NUMBER_OF_UPDATES * NUMBER_OF_CONCURRENT_PUBLISHERS, cachedSaga.getState().size());
        }

        // Destruct the sagas...
        for (String sagaName : SAGA_NAMES) {
            publish(new CachedSaga.SagaEndsEvent(sagaName + "-id", true));
        }
        await().pollDelay(DEFAULT_DELAY)
               .atMost(FOUR_SECONDS)
               .until(() -> handledEventsUpTo(
                       createEvents + (NUMBER_OF_UPDATES * SAGA_NAMES.length) + deleteEvents
               ));

        // Validate association cache is empty
        for (String sagaName : SAGA_NAMES) {
            await().pollDelay(DEFAULT_DELAY)
                   .atMost(FOUR_SECONDS)
                   .until(() -> associationsCacheListener.isRemoved(sagaAssociationCacheKey(sagaName + "-id")));
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
        config.eventBus().publish(eventMessages);
    }

    private Boolean handledEventsUpTo(int handledEvents) {
        return sagaProcessor.processingStatus()
                            .values()
                            .stream()
                            .map(status -> status.getCurrentPosition().orElse(-1L) >= handledEvents - 1)
                            .reduce(Boolean::logicalAnd)
                            .orElse(false);
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

    /**
     * {@link org.axonframework.common.caching.Cache.EntryListener} implementation used for validating the correct
     * workings of caching within Axon Framework. Used instead of validating the {@link Cache} directly, as it may have
     * changed while the given and/or when phase of a test is processing.
     *
     * @param <V> The type of value listened to through the {@link Cache}.
     */
    private static class EntryListenerValidator<V> implements Cache.EntryListener {

        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final ListenerType type;
        private final Set<String> created = new ConcurrentSkipListSet<>();
        private final Set<String> removed = new ConcurrentSkipListSet<>();
        private final Set<String> expired = new ConcurrentSkipListSet<>();
        private final Map<String, V> updates = new ConcurrentHashMap<>();

        private EntryListenerValidator(ListenerType type) {
            this.type = type;
        }

        @Override
        public void onEntryExpired(Object key) {
            expired.add(key.toString());
        }

        @Override
        public void onEntryRemoved(Object key) {
            removed.add(key.toString());
        }

        @Override
        public void onEntryUpdated(Object key, Object value) {
            //noinspection unchecked
            updates.put(key.toString(), (V) value);
        }

        @Override
        public void onEntryCreated(Object key, Object value) {
            String keyAsString = key.toString();
            //noinspection unchecked
            updates.put(keyAsString, (V) value);
            boolean added = created.add(keyAsString);
            if (!added) {
                removed.remove(keyAsString);
                expired.remove(keyAsString);
            }
        }

        @Override
        public void onEntryRead(Object key, Object value) {
            // Not used for validation, as the EhCacheAdapter does not support this.
        }

        @Override
        public Object clone() {
            throw new UnsupportedOperationException();
        }

        public Optional<V> get(String key) {
            return Optional.ofNullable(updates.get(key));
        }

        public boolean isRemoved(String key) {
            return removed.contains(key) || expired.contains(key);
        }
    }

    private enum ListenerType {
        SAGA, ASSOCIATIONS
    }
}
