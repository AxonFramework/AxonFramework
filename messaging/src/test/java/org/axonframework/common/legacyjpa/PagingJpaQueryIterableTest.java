/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.common.legacyjpa;

import org.axonframework.common.transaction.NoOpTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.spy;

class PagingJpaQueryIterableTest {

    private final TransactionManager transactionManager = spy(new NoOpTransactionManager());
    // We use te the jpatest which includes the simple TestJpaEntry as entity
    private final EntityManagerFactory emf = Persistence.createEntityManagerFactory("jpatest");
    private final EntityManager entityManager = emf.createEntityManager();
    private EntityTransaction transaction;

    @BeforeEach
    public void setUpJpa() {
        transaction = entityManager.getTransaction();
        transaction.begin();
    }

    @AfterEach
    public void rollback() {
        transaction.rollback();
    }

    @Test
    void queriesJustOneItemAsOnePage() {
        entityManager.persist(new TestJpaEntry("1"));

        PagingJpaQueryIterable<TestJpaEntry, String> iterable = new PagingJpaQueryIterable<>(
                10,
                transactionManager,
                () -> entityManager.createQuery("select t from TestJpaEntry t", TestJpaEntry.class),
                TestJpaEntry::getId);

        List<String> result = StreamSupport.stream(iterable.spliterator(), false).collect(Collectors.toList());
        assertEquals(1, result.size());
        assertEquals("1", result.get(0));
    }

    @Test
    void queriesMultiplePages() {
        List<String> wantedIds = IntStream.range(0, 102).mapToObj(i -> "" + i).collect(Collectors.toList());
        wantedIds.forEach(item -> {
            entityManager.persist(new TestJpaEntry(item));
        });

        PagingJpaQueryIterable<TestJpaEntry, String> iterable = new PagingJpaQueryIterable<>(
                10,
                transactionManager,
                () -> entityManager.createQuery("select t from TestJpaEntry t", TestJpaEntry.class),
                TestJpaEntry::getId);

        List<String> result = StreamSupport.stream(iterable.spliterator(), false).collect(Collectors.toList());
        assertEquals(wantedIds.size(), result.size());
        wantedIds.forEach(id -> {
            assertTrue(result.contains(id));
        });
    }

    @Test
    void throwsExceptionWhenNoItemPresent() {
        PagingJpaQueryIterable<TestJpaEntry, String> iterable = new PagingJpaQueryIterable<>(
                10,
                transactionManager,
                () -> entityManager.createQuery("select t from TestJpaEntry t", TestJpaEntry.class),
                TestJpaEntry::getId);
        Iterator<String> iterator = iterable.iterator();
        assertThrows(NoSuchElementException.class, iterator::next);
    }
}
