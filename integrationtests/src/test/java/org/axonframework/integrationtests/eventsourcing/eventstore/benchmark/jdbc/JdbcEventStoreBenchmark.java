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

package org.axonframework.integrationtests.eventsourcing.eventstore.benchmark.jdbc;

import org.axonframework.common.jdbc.UnitOfWorkAwareConnectionProviderWrapper;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jdbc.MySqlEventTableFactory;
import org.axonframework.integrationtests.eventsourcing.eventstore.benchmark.AbstractEventStoreBenchmark;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.SQLException;
import javax.annotation.Nonnull;
import javax.sql.DataSource;

/**
 * @author Rene de Waele
 */
public class JdbcEventStoreBenchmark extends AbstractEventStoreBenchmark {

    private final DataSource dataSource;
    private final PlatformTransactionManager transactionManager;

    public static void main(String[] args) {
        ApplicationContext context = new ClassPathXmlApplicationContext("META-INF/spring/benchmark-jdbc-context.xml");
        AbstractEventStoreBenchmark benchmark = context.getBean(AbstractEventStoreBenchmark.class);
        benchmark.start();
    }

    public JdbcEventStoreBenchmark(DataSource dataSource, PlatformTransactionManager transactionManager) {
        super(JdbcEventStorageEngine.builder()
                                    .connectionProvider(new UnitOfWorkAwareConnectionProviderWrapper(dataSource::getConnection))
                                    .transactionManager(NoTransactionManager.INSTANCE)
                                    .build());
        this.dataSource = dataSource;
        this.transactionManager = transactionManager;
    }

    @Override
    protected void prepareForBenchmark() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(@Nonnull TransactionStatus status) {
                try {
                    Connection connection = dataSource.getConnection();
                    connection.prepareStatement("DROP TABLE IF EXISTS DomainEventEntry").executeUpdate();
                    connection.prepareStatement("DROP TABLE IF EXISTS SnapshotEventEntry").executeUpdate();
                    ((JdbcEventStorageEngine) getStorageEngine()).createSchema(MySqlEventTableFactory.INSTANCE);
                } catch (SQLException e) {
                    throw new IllegalStateException("Failed to drop or create event table", e);
                }
            }
        });
        super.prepareForBenchmark();
    }
}
