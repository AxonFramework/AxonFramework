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
import org.axonframework.common.jpa.EntityManagerExecutor;
import org.axonframework.conversion.CachingSupplier;
import org.axonframework.conversion.Converter;
import org.axonframework.conversion.json.JacksonConverter;
import org.axonframework.messaging.core.EmptyApplicationContext;
import org.axonframework.messaging.core.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.transaction.jpa.JpaTransactionalExecutorProvider;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
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
 *
 * @author Mateusz Nowak
 * @since 5.0.2
 * @see SpringJpaDeadLetteringIntegrationTest
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

    @Override
    protected SequencedDeadLetterQueue<EventMessage> buildDeadLetterQueue() {
        var jacksonConverter = new JacksonConverter();
        var eventConverter = new DelegatingEventConverter(jacksonConverter);

        return JpaSequencedDeadLetterQueue.<EventMessage>builder()
                                          .processingGroup(PROCESSING_GROUP)
                                          .transactionalExecutorProvider(new JpaTransactionalExecutorProvider(entityManagerFactory))
                                          .eventConverter(eventConverter)
                                          .genericConverter(jacksonConverter)
                                          .build();
    }

    /**
     * Builds a {@link UnitOfWorkFactory} that registers a
     * {@link JpaTransactionalExecutorProvider#SUPPLIER_KEY SUPPLIER_KEY} resource on each
     * {@link org.axonframework.messaging.core.unitofwork.ProcessingContext}, similar to how
     * {@link org.axonframework.extension.spring.messaging.unitofwork.SpringTransactionManager} populates this resource
     * in production.
     * <p>
     * This allows the {@link JpaTransactionalExecutorProvider} to extract the {@link EntityManagerExecutor} from the
     * context when the event processor handles events, exercising the same code path as production.
     */
    @Override
    protected UnitOfWorkFactory buildUnitOfWorkFactory() {
        return new SimpleUnitOfWorkFactory(
                EmptyApplicationContext.INSTANCE,
                config -> config.registerProcessingLifecycleEnhancer(
                        processingLifecycle ->
                                processingLifecycle.runOnPreInvocation(pc -> {
                                    EntityManager em = entityManagerFactory.createEntityManager();
                                    em.getTransaction().begin();
                                    pc.putResource(
                                            JpaTransactionalExecutorProvider.SUPPLIER_KEY,
                                            CachingSupplier.of(() -> new EntityManagerExecutor(() -> em))
                                    );
                                    pc.runOnCommit(p -> {
                                        em.getTransaction().commit();
                                        em.close();
                                    });
                                    pc.onError((p, phase, e) -> {
                                        if (em.getTransaction().isActive()) {
                                            em.getTransaction().rollback();
                                        }
                                        em.close();
                                    });
                                })
                )
        );
    }

    /**
     * Returns a {@link JacksonConverter} since the JPA DLQ serializes event payloads to JSON,
     * so deserialization requires a converter that understands the JSON format.
     */
    @Override
    protected Converter converter() {
        return new JacksonConverter();
    }

    /**
     * Clears stale dead-letter entries from the database before each test method. Although
     * {@code @DirtiesContext(AFTER_EACH_TEST_METHOD)} recreates the Spring context (and thus the embedded HSQLDB),
     * an explicit truncation ensures a clean DLQ table even if context recreation doesn't happen for inherited
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
