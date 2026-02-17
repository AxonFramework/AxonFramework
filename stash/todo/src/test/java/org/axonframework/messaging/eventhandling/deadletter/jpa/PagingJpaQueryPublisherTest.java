/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.deadletter.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import org.axonframework.common.jpa.EntityManagerExecutor;
import org.axonframework.common.tx.TransactionalExecutor;
import org.axonframework.messaging.eventhandling.deadletter.PublisherTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagingJpaQueryPublisherTest {

    private final EntityManagerFactory emf = Persistence.createEntityManagerFactory("jpatest");
    private final EntityManager entityManager = emf.createEntityManager();
    private final TransactionalExecutor<EntityManager> executor = new EntityManagerExecutor(() -> entityManager);
    private EntityTransaction transaction;

    @BeforeEach
    void setUpJpa() {
        transaction = entityManager.getTransaction();
        transaction.begin();
    }

    @AfterEach
    void rollback() {
        transaction.rollback();
    }

    @Test
    void publishesJustOneItemAsOnePage() {
        entityManager.persist(new TestJpaEntry("001"));

        PagingJpaQueryPublisher<TestJpaEntry, String> publisher = publisher(TestJpaEntry::getId);
        List<String> result = PublisherTestUtils.collect(publisher);

        assertEquals(List.of("001"), result);
    }

    @Test
    void publishesMultiplePagesInOrder() {
        List<String> expectedIds = IntStream.range(0, 102)
                                            .mapToObj(i -> "%03d".formatted(i))
                                            .toList();
        expectedIds.forEach(id -> entityManager.persist(new TestJpaEntry(id)));

        PagingJpaQueryPublisher<TestJpaEntry, String> publisher = publisher(TestJpaEntry::getId);
        List<String> result = PublisherTestUtils.collect(publisher);

        assertEquals(expectedIds, result);
    }

    @Test
    void respectsBackpressureAndCancellation() throws InterruptedException {
        IntStream.range(0, 20).mapToObj(i -> "%03d".formatted(i)).forEach(id -> entityManager.persist(new TestJpaEntry(id)));
        PagingJpaQueryPublisher<TestJpaEntry, String> publisher = publisher(TestJpaEntry::getId);

        AtomicReference<Flow.Subscription> subscriptionRef = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        List<String> result = new ArrayList<>();
        CountDownLatch receivedFive = new CountDownLatch(1);

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriptionRef.set(subscription);
            }

            @Override
            public void onNext(String item) {
                result.add(item);
                if (result.size() == 5) {
                    subscriptionRef.get().cancel();
                    receivedFive.countDown();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                receivedFive.countDown();
            }

            @Override
            public void onComplete() {
                completed.set(true);
                receivedFive.countDown();
            }
        });

        Flow.Subscription subscription = subscriptionRef.get();
        assertNotNull(subscription);
        subscription.request(5);

        assertTrue(receivedFive.await(1, TimeUnit.SECONDS));
        assertEquals(5, result.size());
        assertFalse(completed.get());
    }

    @Test
    void emitsErrorWhenMapperFails() throws InterruptedException {
        entityManager.persist(new TestJpaEntry("001"));
        PagingJpaQueryPublisher<TestJpaEntry, String> publisher = publisher(entry -> {
            throw new IllegalStateException("mapping-failed");
        });

        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {
                // no-op
            }

            @Override
            public void onError(Throwable throwable) {
                errorRef.set(throwable);
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });

        assertTrue(done.await(1, TimeUnit.SECONDS));
        assertTrue(errorRef.get() instanceof IllegalStateException);
    }

    @Test
    void emitsErrorWhenRequestingNonPositiveDemand() throws InterruptedException {
        entityManager.persist(new TestJpaEntry("001"));
        PagingJpaQueryPublisher<TestJpaEntry, String> publisher = publisher(TestJpaEntry::getId);

        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(0);
            }

            @Override
            public void onNext(String item) {
                // no-op
            }

            @Override
            public void onError(Throwable throwable) {
                errorRef.set(throwable);
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });

        assertTrue(done.await(1, TimeUnit.SECONDS));
        assertTrue(errorRef.get() instanceof IllegalArgumentException);
    }

    private PagingJpaQueryPublisher<TestJpaEntry, String> publisher(java.util.function.Function<TestJpaEntry, String> mapper) {
        return new PagingJpaQueryPublisher<>(
                10,
                Runnable::run,
                executor,
                em -> em.createQuery("select t from TestJpaEntry t order by t.id", TestJpaEntry.class),
                mapper
        );
    }
}
