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

package org.axonframework.extension.springboot.messaging.deadletter.jdbc;

import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.deadletter.DeadLetteringEventIntegrationTest;
import org.axonframework.messaging.eventhandling.deadletter.SequencedDeadLetterQueueFactory;
import org.axonframework.messaging.eventhandling.deadletter.jdbc.GenericDeadLetterTableFactory;
import org.axonframework.messaging.eventhandling.deadletter.jdbc.JdbcSequencedDeadLetterQueue;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.annotation.DirtiesContext;

import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

/**
 * A Spring Boot-idiomatic implementation of the {@link DeadLetteringEventIntegrationTest} validating the
 * {@link JdbcSequencedDeadLetterQueue} with full Spring Boot auto-configuration.
 * <p>
 * This test leverages Spring Boot auto-configuration for JDBC infrastructure ({@code DataSource}) instead of manually
 * wiring these beans. An embedded H2 in-memory database is configured via properties.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 */
@SpringBootTest(properties = {
        "axon.axonserver.enabled=false",
        "spring.main.banner-mode=off",
        "spring.datasource.generate-unique-name=true"
})
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        JmxAutoConfiguration.class,
        WebClientAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
class SpringBootJdbcDeadLetteringIntegrationTest extends DeadLetteringEventIntegrationTest {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private UnitOfWorkFactory unitOfWorkFactory;
    @Autowired
    private EventConverter eventConverter;
    @Autowired
    private SequencedDeadLetterQueueFactory deadLetterQueueFactory;
    @Autowired
    private Configuration configuration;

    @Override
    protected JdbcSequencedDeadLetterQueue<EventMessage> buildDeadLetterQueue() {
        var jdbcDeadLetterQueue = (JdbcSequencedDeadLetterQueue<EventMessage>) deadLetterQueueFactory.create(
                PROCESSING_GROUP,
                configuration);
        dropSchemaIfExists();
        jdbcDeadLetterQueue.createSchema(new GenericDeadLetterTableFactory(), null)
                           .orTimeout(10, TimeUnit.SECONDS).join();
        return jdbcDeadLetterQueue;
    }

    private void dropSchemaIfExists() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE DeadLetterEntry IF EXISTS");
        } catch (Exception e) {
            // Ignore — table may not exist on the first run
        }
    }

    /**
     * Delegates to the auto-configured
     * {@link org.axonframework.messaging.core.unitofwork.transaction.TransactionManager} (a
     * {@link org.axonframework.extension.spring.messaging.unitofwork.SpringTransactionManager}) which automatically
     * registers {@link JdbcTransactionalExecutorProvider#SUPPLIER_KEY} on the
     * {@link org.axonframework.messaging.core.unitofwork.ProcessingContext}, exercising the same code path as
     * production.
     */
    @Override
    protected UnitOfWorkFactory buildUnitOfWorkFactory() {
        return unitOfWorkFactory;
    }

    /**
     * Returns the autowired {@link EventConverter} since the JDBC DLQ serializes event payloads to JSON, so
     * deserialization requires a converter that understands the JSON format.
     */
    @Override
    protected EventConverter eventConverter() {
        return eventConverter;
    }

    /**
     * Clears stale dead-letter entries from the database before each test method. Although
     * {@code @DirtiesContext(AFTER_EACH_TEST_METHOD)} recreates the Spring context (and thus the embedded H2), an
     * explicit truncation ensures a clean DLQ table even if context recreation doesn't happen for inherited
     * {@code @Nested} test classes.
     * <p>
     * The {@code buildDeadLetterQueue()} method (called from the parent {@code @BeforeEach}) runs before this method,
     * so the schema is already created when this cleanup executes.
     */
    @BeforeEach
    void cleanDatabase() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM DeadLetterEntry");
        }
    }
}
