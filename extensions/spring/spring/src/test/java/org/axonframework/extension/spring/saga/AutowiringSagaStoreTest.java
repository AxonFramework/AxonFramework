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

package org.axonframework.extension.spring.saga;

import org.axonframework.modelling.saga.AssociationValue;
import org.axonframework.modelling.saga.AssociationValuesImpl;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.inmemory.InMemorySagaStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class validating that {@link AutowiringSagaStore} hands a Saga read back from its store the resources Spring
 * would have injected into a newly created one.
 *
 * @author Mateusz Nowak
 */
class AutowiringSagaStoreTest {

    private static final AssociationValue ORDER_1 = new AssociationValue("orderId", "order-1");

    private AnnotationConfigApplicationContext applicationContext;
    private InMemorySagaStore delegate;
    private SagaStore<Object> testSubject;

    @BeforeEach
    void setUp() {
        applicationContext = new AnnotationConfigApplicationContext(Context.class);
        delegate = new InMemorySagaStore();
        testSubject = new AutowiringSagaStore<>(delegate, applicationContext.getAutowireCapableBeanFactory());
    }

    @AfterEach
    void tearDown() {
        applicationContext.close();
    }

    @Nested
    class LoadingASaga {

        @Test
        void injectsAnAnnotatedField() {
            // given a Saga stored without ever passing through the bean factory, as a deserialized one has not
            delegate.insertSaga(InjectableSaga.class, "saga-1", new InjectableSaga(), Set.of(ORDER_1));

            // when
            SagaStore.Entry<InjectableSaga> entry = testSubject.loadSaga(InjectableSaga.class, "saga-1");

            // then
            assertThat(entry).isNotNull();
            assertThat(entry.saga().collaborator).isNotNull();
        }

        @Test
        void leavesAFieldWithoutTheAnnotationAlone() {
            // given
            delegate.insertSaga(InjectableSaga.class, "saga-1", new InjectableSaga(), Set.of(ORDER_1));

            // when
            SagaStore.Entry<InjectableSaga> entry = testSubject.loadSaga(InjectableSaga.class, "saga-1");

            // then, as Axon Framework 4 also left it, since only annotation-driven injection is applied
            assertThat(entry).isNotNull();
            assertThat(entry.saga().unannotatedCollaborator).isNull();
        }

        @Test
        void leavesAnOptionalSetterAloneWhenNothingSatisfiesIt() {
            // given
            delegate.insertSaga(InjectableSaga.class, "saga-1", new InjectableSaga(), Set.of(ORDER_1));

            // when
            SagaStore.Entry<InjectableSaga> entry = testSubject.loadSaga(InjectableSaga.class, "saga-1");

            // then
            assertThat(entry).isNotNull();
            assertThat(entry.saga().absentCollaborator).isNull();
        }

        @Test
        void keepsTheAssociationValuesOfTheEntry() {
            // given
            delegate.insertSaga(InjectableSaga.class, "saga-1", new InjectableSaga(), Set.of(ORDER_1));

            // when
            SagaStore.Entry<InjectableSaga> entry = testSubject.loadSaga(InjectableSaga.class, "saga-1");

            // then
            assertThat(entry).isNotNull();
            assertThat(entry.saga()).isNotNull();
            assertThat(entry.associationValues()).containsExactly(ORDER_1);
        }

        @Test
        void reportsAnUnsatisfiableRequiredResource() {
            // given a Saga demanding a resource the application context does not hold
            delegate.insertSaga(DemandingSaga.class, "saga-1", new DemandingSaga(), Set.of(ORDER_1));

            // when / then, rather than handing out a half-initialized Saga
            assertThatThrownBy(() -> testSubject.loadSaga(DemandingSaga.class, "saga-1"))
                    .isInstanceOf(BeanCreationException.class);
        }

        @Test
        void passesOnTheAbsenceOfASaga() {
            // given nothing stored

            // when / then, since a Saga may cease to exist between being found and being loaded
            assertThat(testSubject.loadSaga(InjectableSaga.class, "unknown")).isNull();
        }
    }

    @Nested
    class EverythingElse {

        @Test
        void insertsTheSagaAsGiven() {
            // given a Saga that came from the bean factory, and so needs nothing done to it
            InjectableSaga saga = new InjectableSaga();

            // when
            testSubject.insertSaga(InjectableSaga.class, "saga-1", saga, Set.of(ORDER_1));

            // then
            SagaStore.Entry<InjectableSaga> stored = delegate.loadSaga(InjectableSaga.class, "saga-1");
            assertThat(stored).isNotNull();
            assertThat(stored.saga()).isSameAs(saga);
        }

        @Test
        void updatesTheSaga() {
            // given
            InjectableSaga saga = new InjectableSaga();
            testSubject.insertSaga(InjectableSaga.class, "saga-1", saga, Set.of());
            AssociationValuesImpl associationValues = new AssociationValuesImpl();
            associationValues.add(ORDER_1);

            // when
            testSubject.updateSaga(InjectableSaga.class, "saga-1", saga, associationValues);

            // then
            assertThat(delegate.findSagas(InjectableSaga.class, ORDER_1)).containsExactly("saga-1");
        }

        @Test
        void deletesTheSaga() {
            // given
            testSubject.insertSaga(InjectableSaga.class, "saga-1", new InjectableSaga(), Set.of(ORDER_1));

            // when
            testSubject.deleteSaga(InjectableSaga.class, "saga-1", Set.of(ORDER_1));

            // then
            assertThat(delegate.loadSaga(InjectableSaga.class, "saga-1")).isNull();
        }

        @Test
        void findsSagas() {
            // given
            testSubject.insertSaga(InjectableSaga.class, "saga-1", new InjectableSaga(), Set.of(ORDER_1));

            // when / then
            assertThat(testSubject.findSagas(InjectableSaga.class, ORDER_1)).containsExactly("saga-1");
        }
    }

    @SuppressWarnings("unused")
    static class InjectableSaga {

        @Autowired
        private Collaborator collaborator;

        private Collaborator unannotatedCollaborator;

        private AbsentCollaborator absentCollaborator;

        @Autowired(required = false)
        void setAbsentCollaborator(AbsentCollaborator absentCollaborator) {
            this.absentCollaborator = absentCollaborator;
        }
    }

    @SuppressWarnings("unused")
    static class DemandingSaga {

        @Autowired
        private AbsentCollaborator absentCollaborator;
    }

    static class Collaborator {

    }

    static class AbsentCollaborator {

    }

    @Configuration
    static class Context {

        @Bean
        Collaborator collaborator() {
            return new Collaborator();
        }
    }
}
