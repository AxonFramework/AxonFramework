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

package org.axonframework.modelling.command;

import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.modelling.command.inspection.AnnotatedAggregate;
import org.junit.jupiter.api.*;

import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AbstractRepository}.
 *
 * @author Allard Buijze
 */
class AbstractRepositoryTest {

    private static final String AGGREGATE_ID = "some-identifier";

    private AbstractRepository<JpaAggregate, AnnotatedAggregate<JpaAggregate>> testSubject;

    private AnnotatedAggregate<JpaAggregate> spiedAggregate;
    private final Message failureMessage = null;

    @BeforeEach
    void setUp() {
        testSubject = new AbstractRepository<JpaAggregate, AnnotatedAggregate<JpaAggregate>>(
                new AbstractRepository.Builder<JpaAggregate>(JpaAggregate.class) {
                }) {

            @Override
            protected AnnotatedAggregate<JpaAggregate> doCreateNew(Callable<JpaAggregate> factoryMethod)
                    throws Exception {
                return AnnotatedAggregate.initialize(factoryMethod, aggregateModel(), null);
            }

            @Override
            protected void doSave(AnnotatedAggregate<JpaAggregate> aggregate) {

            }

            @Override
            protected void doDelete(AnnotatedAggregate<JpaAggregate> aggregate) {

            }

            @Override
            protected AnnotatedAggregate<JpaAggregate> doLoad(String aggregateIdentifier) {
                spiedAggregate = spy(AnnotatedAggregate.initialize(new JpaAggregate(), aggregateModel(), null));

                try {
                    //noinspection ConstantConditions
                    doThrow(new IllegalArgumentException()).when(spiedAggregate).handle(eq(failureMessage), any());
                } catch (Exception e) {
                    // Fail silently for testings sake
                }

                if (!AGGREGATE_ID.equals(aggregateIdentifier)) {
                    throw new AggregateNotFoundException(aggregateIdentifier, "some-message");
                }

                return spiedAggregate;
            }
        };

        LegacyDefaultUnitOfWork.startAndGet(null);
    }

    @AfterEach
    void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    void aggregateTypeVerification_CorrectType() throws Exception {
        testSubject.newInstance(() -> new JpaAggregate("hi"));
    }

    @Test
    void aggregateTypeVerification_SubclassesAreAllowed() throws Exception {
        testSubject.newInstance(() -> new JpaAggregate("hi") {
            // anonymous subclass
        });
    }

    @Test
    void aggregateTypeVerification_WrongType() {
        //noinspection rawtypes
        AbstractRepository anonymousTestSubject =
                new AbstractRepository<JpaAggregate, AnnotatedAggregate<JpaAggregate>>(
                        new AbstractRepository.Builder<JpaAggregate>(JpaAggregate.class) {
                        }) {

                    @Override
                    protected AnnotatedAggregate<JpaAggregate> doCreateNew(Callable<JpaAggregate> factoryMethod)
                            throws Exception {
                        return AnnotatedAggregate.initialize(factoryMethod, aggregateModel(), null);
                    }

                    @Override
                    protected void doSave(AnnotatedAggregate<JpaAggregate> aggregate) {

                    }

                    @Override
                    protected void doDelete(AnnotatedAggregate<JpaAggregate> aggregate) {

                    }

                    @Override
                    protected AnnotatedAggregate<JpaAggregate> doLoad(String aggregateIdentifier) {
                        return null;
                    }
                };

        //noinspection unchecked
        assertThrows(IllegalArgumentException.class, () -> anonymousTestSubject.newInstance(() -> "Not allowed"));
    }

    @Test
    void canResolveReturnsTrueForMatchingAggregateDescriptor() {
        assertTrue(testSubject.canResolve(new AggregateScopeDescriptor(
                JpaAggregate.class.getSimpleName(), AGGREGATE_ID)
        ));
    }

    @Test
    void canResolveReturnsTrueForExplicitAggregateType() {
        assertTrue(testSubject.canResolve(new AggregateScopeDescriptor(
                JpaAggregate.JPA_AGGREGATE_CUSTOM_TYPE_NAME, AGGREGATE_ID)
        ));
    }

    @Test
    void canResolveReturnsFalseForNonMatchingAggregateType() {
        assertFalse(testSubject.canResolve(new AggregateScopeDescriptor("other-non-matching-type", AGGREGATE_ID)));
    }

//    @Test
//    void sendWorksAsExpected() throws Exception {
//        DeadlineMessage testMsg = aDeadlineMessage();
//        ProcessingContext context = StubProcessingContext.forMessage(testMsg);
//        AggregateScopeDescriptor testDescriptor =
//                new AggregateScopeDescriptor(JpaAggregate.class.getSimpleName(), AGGREGATE_ID);
//
//        testSubject.send(testMsg, context, testDescriptor);
//
//        verify(spiedAggregate).handle(testMsg, context);
//    }
//
//    @Test
//    void sendThrowsIllegalArgumentExceptionIfHandleFails() throws Exception {
//        DeadlineMessage testMsg = aDeadlineMessage();
//        ProcessingContext context = StubProcessingContext.forMessage(testMsg);
//        AggregateScopeDescriptor testDescriptor =
//                new AggregateScopeDescriptor(JpaAggregate.class.getSimpleName(), AGGREGATE_ID);
//
//        //noinspection ConstantConditions
//        assertThrows(IllegalArgumentException.class, () -> testSubject.send(failureMessage, context, testDescriptor));
//
//        //noinspection ConstantConditions
//        verify(spiedAggregate).handle(failureMessage, context);
//    }
//
//    @Test
//    void sendFailsSilentlyOnAggregateNotFoundException() throws Exception {
//        DeadlineMessage testMsg = aDeadlineMessage();
//        ProcessingContext context = StubProcessingContext.forMessage(testMsg);
//        AggregateScopeDescriptor testDescriptor =
//                new AggregateScopeDescriptor(JpaAggregate.class.getSimpleName(), "some-other-aggregate-id");
//
//        testSubject.send(testMsg, context, testDescriptor);
//
//        verifyNoInteractions(spiedAggregate);
//    }

    @Test
    void checkedExceptionFromConstructorDoesNotAttemptToStoreAggregate() {
        // committing the unit of work does not throw an exception
        LegacyUnitOfWork<?> uow = CurrentUnitOfWork.get();
        uow.executeWithResult((ctx) -> testSubject.newInstance(() -> {
            throw new Exception("Throwing checked exception");
        }), RuntimeException.class::isInstance);

        assertFalse(uow.isActive());
        assertFalse(uow.isRolledBack());
        assertTrue(uow.getExecutionResult().isExceptionResult());
        assertEquals("Throwing checked exception", uow.getExecutionResult().getExceptionResult().getMessage());
    }

//    private static DeadlineMessage aDeadlineMessage() {
//        var payload = "payload";
//        return new GenericDeadlineMessage(
//                "deadline-name",
//                new GenericMessage(new MessageType(payload.getClass()), payload),
//                Instant::now
//        );
//    }
}
