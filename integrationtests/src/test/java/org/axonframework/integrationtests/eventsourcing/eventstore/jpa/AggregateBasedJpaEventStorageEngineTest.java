/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.integrationtests.eventsourcing.eventstore.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.processors.streaming.token.GapAwareTrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.eventsourcing.eventstore.AggregateBasedConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.AggregateBasedStorageEngineTestSuite;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.eventsourcing.eventstore.jpa.AggregateBasedJpaEventStorageEngine;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.eventstreaming.StreamingCondition;
import org.axonframework.eventstreaming.Tag;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;
import javax.sql.DataSource;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AggregateBasedJpaEventStorageEngine}.
 *
 * @author Mateusz Nowak
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AggregateBasedJpaEventStorageEngineTest.TestContext.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AggregateBasedJpaEventStorageEngineTest
        extends AggregateBasedStorageEngineTestSuite<AggregateBasedJpaEventStorageEngine> {

    @Autowired
    private PlatformTransactionManager platformTransactionManager;
    @Autowired
    private EntityManagerProvider entityManagerProvider;

    private TransactionManager transactionManager;

    @Override
    protected AggregateBasedJpaEventStorageEngine buildStorageEngine() {
        transactionManager = spy(new SpringTransactionManager(platformTransactionManager));
        return new AggregateBasedJpaEventStorageEngine(
                entityManagerProvider,
                transactionManager,
                converter,
                config -> config.persistenceExceptionResolver(new PersistenceExceptionResolver() {
                    @Override
                    public boolean isDuplicateKeyViolation(Exception exception) {
                        return causeIsEntityExistsException(exception);
                    }

                    private boolean causeIsEntityExistsException(Throwable exception) {
                        return exception instanceof java.sql.SQLIntegrityConstraintViolationException
                                || (exception.getCause() != null && causeIsEntityExistsException(exception.getCause()));
                    }
                })
        );
    }

    @Override
    protected ProcessingContext processingContext() {
        return null;
    }

    @Override
    protected long globalSequenceOfEvent(long position) {
        return position;
    }

    @Override
    protected TrackingToken trackingTokenAt(long position) {
        return GapAwareTrackingToken.newInstance(globalSequenceOfEvent(position), emptySet());
    }

    @Override
    protected EventMessage convertPayload(EventMessage original) {
        return original.withConvertedPayload(String.class, converter);
    }

    @Test
    void sourcingFromNonGapAwareTrackingTokenShouldThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> testSubject.stream(StreamingCondition.startingFrom(new GlobalSequenceTrackingToken(5)),
                                         processingContext())
        );
    }

    @Test
    void appendEventsIsPerformedInATransaction() {
        appendCommitAndWait(testSubject, AppendCondition.none(), taggedEventMessage("event-2", emptySet()));

        verify(transactionManager).startTransaction();
    }

    @Test
    void gapsForVeryOldEventsAreNotIncluded() {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        Transaction transaction = transactionManager.startTransaction();
        entityManager.createQuery("DELETE FROM AggregateEventEntry dee").executeUpdate();
        entityManager.clear();
        transaction.commit();

        GenericEventMessage.clock =
                Clock.fixed(Clock.systemUTC().instant().minus(1, ChronoUnit.HOURS), Clock.systemUTC().getZone());
        appendCommitAndWait(testSubject, AppendCondition.none(),
                            taggedEventMessage("-1", Set.of()), taggedEventMessage("0", Set.of()));
        GenericEventMessage.clock =
                Clock.fixed(Clock.systemUTC().instant().minus(2, ChronoUnit.MINUTES), Clock.systemUTC().getZone());
        appendCommitAndWait(testSubject, AppendCondition.none(),
                            taggedEventMessage("-2", Set.of()), taggedEventMessage("1", Set.of()));

        GenericEventMessage.clock =
                Clock.fixed(Clock.systemUTC().instant().minus(50, ChronoUnit.SECONDS), Clock.systemUTC().getZone());
        appendCommitAndWait(testSubject, AppendCondition.none(),
                            taggedEventMessage("-3", Set.of()), taggedEventMessage("2", Set.of()));

        GenericEventMessage.clock = Clock.fixed(Clock.systemUTC().instant(), Clock.systemUTC().getZone());
        appendCommitAndWait(testSubject, AppendCondition.none(),
                            taggedEventMessage("-4", Set.of()), taggedEventMessage("3", Set.of()));

        entityManager.clear();
        transaction.commit();
        transaction = transactionManager.startTransaction();
        entityManager.createQuery("DELETE FROM AggregateEventEntry dee WHERE dee.aggregateSequenceNumber < 0")
                     .executeUpdate();
        transaction.commit();

        testSubject.stream(StreamingCondition.startingFrom(new GapAwareTrackingToken(0, Collections.emptySet())),
                           processingContext())
                   .reduce(
                           new ArrayList<TrackingToken>(), (tokens, entry) -> {
                               Optional<TrackingToken> optionalToken = TrackingToken.fromContext(entry);
                               assertThat(optionalToken).isPresent();
                               tokens.add(optionalToken.get());
                               return tokens;
                           }
                   )
                   .join()
                   .forEach(token -> {
                       assertThat(token).isInstanceOf(GapAwareTrackingToken.class);
                       GapAwareTrackingToken gapAwareToken = (GapAwareTrackingToken) token;
                       assertThat(!gapAwareToken.hasGaps() || gapAwareToken.getGaps().first() >= 5L).isTrue();
                   });

        transaction.commit();
    }

    @Test
    void oldGapsAreRemovedFromProvidedTrackingToken() {
        AggregateBasedJpaEventStorageEngine gapConfigTestSubject = new AggregateBasedJpaEventStorageEngine(
                entityManagerProvider,
                transactionManager,
                converter,
                config -> config.maxGapOffset(10000)
                                .gapTimeout(50001)
                                .gapCleaningThreshold(50)
        );

        EntityManager entityManager = entityManagerProvider.getEntityManager();
        Transaction transaction = transactionManager.startTransaction();
        entityManager.createQuery("DELETE FROM AggregateEventEntry dee").executeUpdate();
        entityManager.clear();
        transaction.commit();

        Instant now = Clock.systemUTC().instant();
        Tag aggregateToRemove = Tag.of("MyAggregate", "remove");
        AppendCondition removeAggregateCondition =
                AppendCondition.withCriteria(EventCriteria.havingTags(aggregateToRemove));
        Tag aggregateToKeep = Tag.of("MyAggregate", "keep");
        AppendCondition keepAggregateCondition =
                AppendCondition.withCriteria(EventCriteria.havingTags(aggregateToKeep));
        GenericEventMessage.clock = Clock.fixed(now.minus(1, ChronoUnit.HOURS), Clock.systemUTC().getZone());
        appendCommitAndWait(gapConfigTestSubject, removeAggregateCondition,
                            taggedEventMessage("-1", Set.of(aggregateToRemove)));
        appendCommitAndWait(gapConfigTestSubject, keepAggregateCondition,
                            taggedEventMessage("1", Set.of(aggregateToKeep)));
        GenericEventMessage.clock = Clock.fixed(now.minus(2, ChronoUnit.MINUTES), Clock.systemUTC().getZone());
        appendCommitAndWait(gapConfigTestSubject,
                            removeAggregateCondition.withMarker(new AggregateBasedConsistencyMarker("remove", 1)),
                            taggedEventMessage("-2", Set.of(aggregateToRemove)));
        appendCommitAndWait(gapConfigTestSubject,
                            keepAggregateCondition.withMarker(new AggregateBasedConsistencyMarker("keep", 1)),
                            taggedEventMessage("2", Set.of(aggregateToKeep)));
        GenericEventMessage.clock = Clock.fixed(now.minus(50, ChronoUnit.SECONDS), Clock.systemUTC().getZone());
        appendCommitAndWait(gapConfigTestSubject,
                            removeAggregateCondition.withMarker(new AggregateBasedConsistencyMarker("remove", 3)),
                            taggedEventMessage("-3", Set.of(aggregateToRemove)));
        appendCommitAndWait(gapConfigTestSubject,
                            keepAggregateCondition.withMarker(new AggregateBasedConsistencyMarker("keep", 3)),
                            taggedEventMessage("3", Set.of(aggregateToKeep)));
        GenericEventMessage.clock = Clock.fixed(now, Clock.systemUTC().getZone());
        appendCommitAndWait(gapConfigTestSubject,
                            keepAggregateCondition.withMarker(new AggregateBasedConsistencyMarker("remove", 5)),
                            taggedEventMessage("-4", Set.of(aggregateToRemove)));
        appendCommitAndWait(gapConfigTestSubject,
                            keepAggregateCondition.withMarker(new AggregateBasedConsistencyMarker("keep", 5)),
                            taggedEventMessage("4", Set.of(aggregateToKeep)));

        // Let's create some gaps by removing all events where the aggregate identifier is not "remove"
        transaction = transactionManager.startTransaction();
        entityManager.createQuery(
                             "DELETE FROM AggregateEventEntry entry WHERE entry.aggregateIdentifier = :aggregateIdentifier"
                     )
                     .setParameter("aggregateIdentifier", "remove")
                     .executeUpdate();
        entityManager.clear();
        transaction.commit();

        transaction = transactionManager.startTransaction();
        // Some "magic" because sequences aren't reset between tests. Finding the sequence positions to use in assertions
        List<Long> sequences =
                entityManager.createQuery(
                                     "SELECT e.globalIndex FROM AggregateEventEntry e WHERE e.aggregateIdentifier = :aggregateIdentifier",
                                     Long.class
                             )
                             .setParameter("aggregateIdentifier", "keep")
                             .getResultList();
        entityManager.clear();
        transaction.commit();
        Optional<Long> maxResult = sequences.stream().max(Long::compareTo);
        assertThat(maxResult).isPresent();

        long largestIndex = maxResult.get();
        long secondLastEventIndex = largestIndex - 2;
        // create a lot of gaps most of them fake (< 0), but some of them real
        List<Long> gaps = LongStream.range(-50, largestIndex)
                                    .boxed()
                                    .filter(g -> !sequences.contains(g))
                                    .filter(g -> g < secondLastEventIndex)
                                    .collect(toList());
        GapAwareTrackingToken startPosition = GapAwareTrackingToken.newInstance(secondLastEventIndex, gaps);

        MessageStream<EventMessage> eventStream =
                gapConfigTestSubject.stream(StreamingCondition.startingFrom(startPosition), processingContext());
        assertThat(eventStream.hasNextAvailable()).isTrue();
        TrackingToken token = eventStream.next()
                                         .flatMap(TrackingToken::fromContext)
                                         .orElseThrow(AssertionError::new);
        // We should've received a single event.
        assertThat(eventStream.hasNextAvailable()).isFalse();

        assertThat(token).isInstanceOf(GapAwareTrackingToken.class);
        GapAwareTrackingToken resultToken = (GapAwareTrackingToken) token;

        // we expect the gap before the last event we had read previously
        assertThat(resultToken.getGaps().first()).isEqualTo(secondLastEventIndex - 1);
        // and we've got a new gap in this batch
        assertThat(resultToken.getGaps().size()).isEqualTo(2);

        transaction.commit();
    }

    private void appendCommitAndWait(AggregateBasedJpaEventStorageEngine subject,
                                     AppendCondition condition,
                                     TaggedEventMessage<?>... events) {
        subject.appendEvents(condition, processingContext(), events)
               .thenApply(this::castTransaction)
               .thenCompose(tx -> tx.commit(processingContext())
                                    .thenCompose(v -> tx.afterCommit(v, processingContext())))
               .join();
    }

    @Configuration
    public static class TestContext {

        @Configuration
        public static class PersistenceConfig {

            @PersistenceContext
            private EntityManager entityManager;

            @Bean
            public EntityManagerProvider entityManagerProvider() {
                return new SimpleEntityManagerProvider(entityManager);
            }
        }

        @Bean
        public DataSource dataSource() {
            String uniqueDbName = "jdbc:hsqldb:mem:aggregatebasedjpaeventstorageenginetest-" + System.nanoTime();
            DriverManagerDataSource driverManagerDataSource =
                    new DriverManagerDataSource(uniqueDbName, "sa", "password");
            driverManagerDataSource.setDriverClassName("org.hsqldb.jdbcDriver");
            return driverManagerDataSource;
        }

        @Bean("entityManagerFactory")
        public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean entityManagerFactoryBean =
                    new LocalContainerEntityManagerFactoryBean();
            entityManagerFactoryBean.setPersistenceUnitName("integrationtest");

            HibernateJpaVendorAdapter jpaVendorAdapter = new HibernateJpaVendorAdapter();
            jpaVendorAdapter.setDatabasePlatform("org.hibernate.dialect.HSQLDialect");
            jpaVendorAdapter.setGenerateDdl(true);
            jpaVendorAdapter.setShowSql(false);

            entityManagerFactoryBean.setJpaVendorAdapter(jpaVendorAdapter);
            entityManagerFactoryBean.setDataSource(dataSource);

            return entityManagerFactoryBean;
        }

        @Bean
        @DependsOn("entityManagerFactory")
        public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory,
                                                        DataSource dataSource) {
            JpaTransactionManager jpaTransactionManager = new JpaTransactionManager(entityManagerFactory);
            jpaTransactionManager.setDataSource(dataSource);
            return jpaTransactionManager;
        }

        @Bean
        public PersistenceAnnotationBeanPostProcessor persistenceAnnotationBeanPostProcessor() {
            return new PersistenceAnnotationBeanPostProcessor();
        }
    }
}