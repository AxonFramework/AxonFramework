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

package org.axonframework.integrationtests.deadline.dbscheduler;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.SchedulerBuilder;
import com.github.kagkarlsson.scheduler.task.Task;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.config.Configuration;
import org.axonframework.config.ConfigurationScopeAwareProvider;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.dbscheduler.DbSchedulerDeadlineManager;
import org.axonframework.integrationtests.deadline.AbstractDeadlineManagerTestSuite;
import org.axonframework.serialization.TestSerializer;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

@ContextConfiguration
@ExtendWith(MockitoExtension.class)
@ExtendWith(SpringExtension.class)
class DbSchedulerDeadlineManagerTest extends AbstractDeadlineManagerTestSuite {

    @Autowired
    private DataSource dataSource;
    private Scheduler scheduler;

    @AfterEach
    void cleanUp() {
        if (!Objects.isNull(scheduler)) {
            scheduler.stop();
            scheduler = null;
        }
    }

    @Override
    public DeadlineManager buildDeadlineManager(Configuration configuration) {
        reCreateTable();
        List<Task<?>> taskList = Collections.singletonList(DbSchedulerDeadlineManager.task());
        scheduler = new SchedulerBuilder(dataSource, taskList)
                .threads(2)
                .pollingInterval(Duration.ofMillis(50L))
                .build();
        scheduler.start();
        return DbSchedulerDeadlineManager
                .builder()
                .scheduler(scheduler)
                .scopeAwareProvider(new ConfigurationScopeAwareProvider(configuration))
                .serializer(TestSerializer.JACKSON.getSerializer())
                .transactionManager(NoTransactionManager.INSTANCE)
                .spanFactory(configuration.spanFactory())
                .build();
    }

    @SuppressWarnings("Duplicates")
    private void reCreateTable() {
        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        try (PreparedStatement statement = connection.prepareStatement("drop table if exists scheduled_tasks;")) {
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        try (PreparedStatement statement =
                     connection.prepareStatement(
                             "create table scheduled_tasks (\n"
                                     + "  task_name varchar(40) not null,\n"
                                     + "  task_instance varchar(40) not null,\n"
                                     + "  task_data blob,\n"
                                     + "  execution_time timestamp(6) not null,\n"
                                     + "  picked BOOLEAN not null,\n"
                                     + "  picked_by varchar(50),\n"
                                     + "  last_success timestamp(6) null,\n"
                                     + "  last_failure timestamp(6) null,\n"
                                     + "  consecutive_failures INT,\n"
                                     + "  last_heartbeat timestamp(6) null,\n"
                                     + "  version BIGINT not null,\n"
                                     + "  PRIMARY KEY (task_name, task_instance),\n"
                                     + ")")) {
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @org.springframework.context.annotation.Configuration
    public static class Context {

        @SuppressWarnings("Duplicates")
        @Bean
        public DataSource dataSource() {
            JDBCDataSource dataSource = new JDBCDataSource();
            dataSource.setUrl("jdbc:hsqldb:mem:testdb");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }
    }
}
