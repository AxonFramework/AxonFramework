/*
 * Copyright (c) 2010-2015. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.integrationtests.eventstore.benchmark;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/infrastructure-context.xml")
public class JpaInsertionReadOrderTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Inject
    private PlatformTransactionManager tx;
    private TransactionTemplate txReadCommitted;
    private TransactionTemplate txReadUncommitted;

    @Before
    public void setUp() throws Exception {
        txReadCommitted = new TransactionTemplate(tx);
        txReadCommitted.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        txReadUncommitted = new TransactionTemplate(tx);
        txReadUncommitted.setIsolationLevel(TransactionDefinition.ISOLATION_READ_UNCOMMITTED);

        txReadUncommitted
                .execute(ts -> entityManager.createNativeQuery("TRUNCATE TABLE PersistentObject").executeUpdate());
        List<Object[]> results = (List<Object[]>) txReadUncommitted
                .execute(ts -> entityManager.createNativeQuery("SHOW VARIABLES LIKE 'auto_inc%';").getResultList());
        for (Object[] result : results) {
            System.out.print(result[0].toString());
            System.out.println(" " + result[1].toString());
        }
    }

    @Test
    public void testInsertConcurrentlyAndCheckReadOrder() throws Exception {
        System.out.println("Getting ready....");
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            final int t = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    final int s = j;
                    txReadCommitted.execute(ts -> {
                        entityManager.persist(new PersistentObject("Thread" + t, s));
                        entityManager.flush();
                        if (s % 7 == 0) {
                            ts.setRollbackOnly();
                        }
                        try {
                            Thread.sleep(ThreadLocalRandom.current().nextInt(10));
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        return null;
                    });
                }
            });
            threads[i].start();
        }

        List<PersistentObject> readFromDB = new ArrayList<>();
        long lastSeq = -1;
        while (lastSeq < 1000) {
            long seq = lastSeq;
            List<PersistentObject> batch = txReadCommitted.execute(ts -> entityManager
                    .createQuery("SELECT p FROM PersistentObject p WHERE p.id > :lastSeq ORDER BY p.id",
                                 PersistentObject.class).setParameter("lastSeq", seq).setMaxResults(20)
                    .getResultList());
            for (PersistentObject persistentObject : batch) {
                if (noGaps(lastSeq, persistentObject.getId())) {
                    readFromDB.add(persistentObject);
                    System.out.println(
                            persistentObject.getThreadDescription() + " / " + persistentObject.getSeqNo() + " => " +
                                    persistentObject.getId());
                    lastSeq = persistentObject.getId();
                } else {
                    System.out.println("Gap detected " + lastSeq + " -> " + persistentObject.getId() + ". Sleeping");
                    break;
                }
            }
        }

        for (Thread thread : threads) {
            thread.join();
        }

        List<PersistentObject> checklist =
                entityManager.createQuery("SELECT p FROM PersistentObject p ORDER BY p.id", PersistentObject.class)
                        .getResultList();

        assertEquals("Size not equal", checklist.size(), readFromDB.size());

    }

    private boolean noGaps(long lastSeq, Long newSeq) {
        if (newSeq == lastSeq + 1) {
            return true;
        }

        List<Long> missingIds = txReadUncommitted.execute(ts -> entityManager.createQuery(
                "SELECT p.id FROM PersistentObject p WHERE p.id > :seqLast AND p.id < :seqNew ORDER BY p.id",
                Long.class).setParameter("seqLast", lastSeq).setParameter("seqNew", newSeq).getResultList());
        if (missingIds.isEmpty()) {
            System.out.println("Gap appeared to be a rollback: " + lastSeq + " -> " + newSeq);
        }
        return missingIds.isEmpty();
    }
}
