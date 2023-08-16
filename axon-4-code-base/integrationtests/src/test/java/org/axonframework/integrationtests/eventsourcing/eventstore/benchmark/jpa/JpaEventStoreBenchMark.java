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

package org.axonframework.integrationtests.eventsourcing.eventstore.benchmark.jpa;

import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.integrationtests.eventsourcing.eventstore.benchmark.AbstractEventStoreBenchmark;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * @author Jettro Coenradie
 */
public class JpaEventStoreBenchMark extends AbstractEventStoreBenchmark {

    private final TransactionManager transactionManager;

    public static void main(String[] args) {
        ApplicationContext context = new ClassPathXmlApplicationContext("META-INF/spring/benchmark-jpa-context.xml");
        AbstractEventStoreBenchmark benchmark = context.getBean(AbstractEventStoreBenchmark.class);
        benchmark.start();
    }

    public JpaEventStoreBenchMark(JpaEventStorageEngine storageEngine, PlatformTransactionManager transactionManager) {
        super(storageEngine);
        this.transactionManager = new SpringTransactionManager(transactionManager);
    }

    @Override
    protected void storeEvents(EventMessage<?>... events) {
        UnitOfWork<?> unitOfWork = CurrentUnitOfWork.get();
        Transaction transaction = transactionManager.startTransaction();
        unitOfWork.onCommit(u -> transaction.commit());
        unitOfWork.onRollback(u -> transaction.rollback());
        super.storeEvents(events);
    }
}
