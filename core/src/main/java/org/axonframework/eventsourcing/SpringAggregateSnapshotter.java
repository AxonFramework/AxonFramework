/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.eventsourcing;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of the {@link org.axonframework.eventsourcing.AggregateSnapshotter} that eases the configuration when
 * used within a Spring Application Context. It will automatically detect a Transaction Manager and Aggregate
 * Factories.
 * <p/>
 * The only mandatory properties to set is {@link #setEventStore(org.axonframework.eventstore.SnapshotEventStore)}. In
 * most cases, you should also provide an executor, as the default will execute snapshotter tasks in the calling
 * thread.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public class SpringAggregateSnapshotter extends AggregateSnapshotter
        implements InitializingBean, ApplicationContextAware {

    private PlatformTransactionManager transactionManager;
    private boolean autoDetectAggregateFactories = true;
    private ApplicationContext applicationContext;
    private TransactionDefinition transactionDefinition = new DefaultTransactionDefinition();

    @Override
    protected Runnable createSnapshotterTask(String typeIdentifier, Object aggregateIdentifier) {
        Runnable command = super.createSnapshotterTask(typeIdentifier, aggregateIdentifier);
        if (transactionManager != null) {
            return new TransactionalRunnableWrapper(command);
        }
        return command;
    }

    /**
     * Sets the transaction manager to manager underlying transaction with. If none is provided, an attempt is made to
     * auto detect is from the application context. If a single transaction manager is found, it is used to manage
     * transactions. Of none or more than one is found, they are ignored.
     *
     * @param transactionManager the transaction manager managing underlying transactions
     */
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * Optionally sets the transaction definition to use. By default, uses the application context's default
     * transaction semantics (see {@link org.springframework.transaction.support.DefaultTransactionDefinition}).
     *
     * @param transactionDefinition the transaction definition to use
     */
    public void setTransactionDefinition(TransactionDefinition transactionDefinition) {
        this.transactionDefinition = transactionDefinition;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public void afterPropertiesSet() throws Exception {
        if (autoDetectAggregateFactories) {
            Set<AggregateFactory> factoriesFound = new HashSet<AggregateFactory>();
            factoriesFound.addAll(applicationContext.getBeansOfType(AggregateFactory.class).values());
            Collection<EventSourcingRepository> eventSourcingRepositories =
                    applicationContext.getBeansOfType(EventSourcingRepository.class).values();
            for (EventSourcingRepository repo : eventSourcingRepositories) {
                factoriesFound.add(repo.getAggregateFactory());
            }
            setAggregateFactories(new ArrayList(factoriesFound));
        }

        if (transactionManager == null) {
            Map<String, PlatformTransactionManager> candidates = applicationContext.getBeansOfType(
                    PlatformTransactionManager.class);
            if (candidates.size() == 1) {
                this.transactionManager = candidates.values().iterator().next();
            }
        }
    }

    /**
     * Optionally sets the aggregate factories to use. By default, this implementation will auto detect available
     * factories from the application context. Configuring them using this method will prevent auto detection.
     *
     * @param aggregateFactories The list of aggregate factories creating the aggregates to store.
     */
    @Override
    public void setAggregateFactories(List<AggregateFactory<?>> aggregateFactories) {
        this.autoDetectAggregateFactories = false;
        super.setAggregateFactories(aggregateFactories);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private class TransactionalRunnableWrapper implements Runnable {

        private final Runnable command;

        public TransactionalRunnableWrapper(Runnable command) {
            this.command = command;
        }

        @Override
        public void run() {
            TransactionStatus transaction = transactionManager.getTransaction(transactionDefinition);
            try {
                command.run();
                if (transaction.isNewTransaction()) {
                    transactionManager.commit(transaction);
                }
            } catch (RuntimeException e) {
                if (transaction.isNewTransaction()) {
                    transactionManager.rollback(transaction);
                }
                throw e;
            }
        }
    }
}
