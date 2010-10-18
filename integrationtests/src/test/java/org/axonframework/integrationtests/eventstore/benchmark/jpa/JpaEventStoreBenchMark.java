/*
 * Copyright (c) 2010. Gridshore
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

package org.axonframework.integrationtests.eventstore.benchmark.jpa;

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.AggregateIdentifierFactory;
import org.axonframework.eventstore.jpa.JpaEventStore;
import org.axonframework.integrationtests.eventstore.benchmark.AbstractEventStoreBenchmark;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;

/**
 * @author Jettro Coenradie
 */
public class JpaEventStoreBenchMark extends AbstractEventStoreBenchmark {
    private JpaEventStore jpaEventStore;
    private PlatformTransactionManager transactionManager;

    public static void main(String[] args) throws Exception {
        AbstractEventStoreBenchmark benchmark = prepareBenchMark("META-INF/spring/benchmark-jpa-context.xml");
        benchmark.startBenchMark();
    }

    public JpaEventStoreBenchMark(JpaEventStore jpaEventStore, PlatformTransactionManager transactionManager) {
        this.jpaEventStore = jpaEventStore;
        this.transactionManager = transactionManager;
    }

    @Override
    protected void prepareEventStore() {
    }

    @Override
    protected Runnable getRunnableInstance() {
        return new TransactionalBenchmark();
    }

    private class TransactionalBenchmark implements Runnable {

        @Override
        public void run() {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            final AggregateIdentifier aggregateId = AggregateIdentifierFactory.randomIdentifier();
            final AtomicInteger eventSequence = new AtomicInteger(0);
            for (int t = 0; t < getTransactionCount(); t++) {
                template.execute(new TransactionCallbackWithoutResult() {
                    @Override
                    protected void doInTransactionWithoutResult(TransactionStatus status) {
                        assertFalse(status.isRollbackOnly());
                        eventSequence.set(saveAndLoadLargeNumberOfEvents(aggregateId,
                                jpaEventStore,
                                eventSequence.get()) + 1);
                    }
                });
            }
        }
    }

}
