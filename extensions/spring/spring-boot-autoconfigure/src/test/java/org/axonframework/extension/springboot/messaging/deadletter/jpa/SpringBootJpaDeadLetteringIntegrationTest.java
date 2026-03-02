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

package org.axonframework.extension.springboot.messaging.deadletter.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.axonframework.conversion.Converter;
import org.axonframework.conversion.json.JacksonConverter;
import org.axonframework.messaging.core.EmptyApplicationContext;
import org.axonframework.messaging.core.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.TransactionalUnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;
import org.axonframework.messaging.core.unitofwork.transaction.jpa.JpaTransactionalExecutorProvider;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.deadletter.DeadLetteringEventIntegrationTest;
import org.axonframework.messaging.eventhandling.deadletter.jpa.JpaSequencedDeadLetterQueue;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.annotation.DirtiesContext;

/**
 * A Spring Boot-idiomatic implementation of the {@link DeadLetteringEventIntegrationTest} validating the
 * {@link JpaSequencedDeadLetterQueue} with full Spring Boot auto-configuration.
 * <p>
 * Unlike {@link SpringJpaDeadLetteringIntegrationTest}, this test leverages Spring Boot auto-configuration for JPA
 * infrastructure ({@code DataSource}, {@code EntityManagerFactory}, {@code PlatformTransactionManager}) instead of
 * manually wiring these beans. The embedded HSQLDB database is auto-detected by Spring Boot.
 * <p>
 * Transaction management is delegated to the auto-configured
 * {@link org.axonframework.extension.spring.messaging.unitofwork.SpringTransactionManager}, which automatically
 * registers {@link JpaTransactionalExecutorProvider#SUPPLIER_KEY} on the
 * {@link org.axonframework.messaging.core.unitofwork.ProcessingContext} — the same code path used in production.
 *
 * @author Mateusz Nowak
 * @see SpringJpaDeadLetteringIntegrationTest
 * @since 5.0.2
 */
@SpringBootTest(properties = {
        "axon.axonserver.enabled=false",
        "spring.main.banner-mode=off"
})
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        JmxAutoConfiguration.class,
        WebClientAutoConfiguration.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
class SpringBootJpaDeadLetteringIntegrationTest extends DeadLetteringEventIntegrationTest {

    // Must match DeadLetteringEventIntegrationTest.PROCESSING_GROUP (private static final).
    private static final String PROCESSING_GROUP = "problematicProcessingGroup";

    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private UnitOfWorkFactory unitOfWorkFactory;
    @Autowired
    private Converter converter;
    @Autowired
    private EventConverter eventConverter;

    @Override
    protected SequencedDeadLetterQueue<EventMessage> buildDeadLetterQueue() {
        return JpaSequencedDeadLetterQueue.<EventMessage>builder()
                                          .processingGroup(PROCESSING_GROUP)
                                          .transactionalExecutorProvider(new JpaTransactionalExecutorProvider(
                                                  entityManagerFactory))
                                          .eventConverter(eventConverter)
                                          .genericConverter(converter)
                                          .build();
    }

    /**
     * Delegates to the auto-configured {@link TransactionManager} (a
     * {@link org.axonframework.extension.spring.messaging.unitofwork.SpringTransactionManager}) which automatically
     * registers {@link JpaTransactionalExecutorProvider#SUPPLIER_KEY} on the
     * {@link org.axonframework.messaging.core.unitofwork.ProcessingContext}, exercising the same code path as
     * production.
     */
    @Override
    protected UnitOfWorkFactory buildUnitOfWorkFactory() {
        return unitOfWorkFactory;
    }

    /**
     * Returns a {@link JacksonConverter} since the JPA DLQ serializes event payloads to JSON, so deserialization
     * requires a converter that understands the JSON format.
     */
    @Override
    protected Converter converter() {
        return converter;
    }

    /**
     * Clears stale dead-letter entries from the database before each test method. Although
     * {@code @DirtiesContext(AFTER_EACH_TEST_METHOD)} recreates the Spring context (and thus the embedded HSQLDB), an
     * explicit truncation ensures a clean DLQ table even if context recreation doesn't happen for inherited
     * {@code @Nested} test classes.
     * <p>
     * Uses JPQL (entity name) instead of native SQL because Spring Boot's default
     * {@code CamelCaseToUnderscoresNamingStrategy} maps the entity {@code DeadLetterEntry} to the table
     * {@code dead_letter_entry}.
     */
    @BeforeEach
    void cleanDatabase() {
        EntityManager em = entityManagerFactory.createEntityManager();
        em.getTransaction().begin();
        em.createQuery("DELETE FROM DeadLetterEntry").executeUpdate();
        em.getTransaction().commit();
        em.close();
    }
}
