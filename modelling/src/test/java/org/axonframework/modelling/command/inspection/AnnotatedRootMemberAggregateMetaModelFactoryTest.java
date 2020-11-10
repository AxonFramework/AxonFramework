package org.axonframework.modelling.command.inspection;

import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.modelling.command.AggregateMember;
import org.junit.jupiter.api.*;
import org.mockito.internal.util.collections.*;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating an annotated aggregate model with {@link AggregateMember}s on the root level of a polymorphic
 * aggregate behaves as desired when a meta model is created with the {@link AnnotatedAggregateMetaModelFactory}.
  *<p>
 * The hierarchy of the Aggregate, is as follows:
 * <p>
 *          +--------------+
 *          |Root Aggregate|
 *          |   +------+   |
 *          |   |Member|   |
 *          |   +------+   |
 *          +--------------+
 *                 v
 *          +------+-------+
 *          |Node Aggregate|
 *          +------+-------+
 *          v              v
 * +--------+-----+ +------+-------------+
 * |Leaf Aggregate| |Other Leaf Aggregate|
 * +--------------+ +--------------------+
 * <p>
 * On all levels an AggregateEvent handler is present. Only the Member has the MemberEvent handler. In such a set up
 * we would assume the AggregateEvent handler to be invoked once in the root (which encompasses the root, node and
 * leaf aggregate) and once in the member. Furthermore we would anticipate the MemberEvent handler to be invoked
 * once too, since there only is a single occurrence of the member in the entire set up.
 *
 * @author Steven van Beelen
 */
class AnnotatedRootMemberAggregateMetaModelFactoryTest {

    private AtomicInteger aggregateEventCounter ;
    private AtomicInteger memberEventCounter ;

    @BeforeEach
    void setUp() {
        aggregateEventCounter = new AtomicInteger(0);
        memberEventCounter = new AtomicInteger(0);
    }

    @Test
    void testCreateAggregateModelDoesNotDuplicateRootLevelAggregateMembers() {
        int expectedNumberOfAggregateEventHandlerInvocations = 2;
        int expectedNumberOfMemberEventHandlerInvocations = 1;

        EventMessage<AggregateEvent> testAggregateEvent = GenericEventMessage.asEventMessage(new AggregateEvent());
        EventMessage<MemberEvent> testMemberEvent = GenericEventMessage.asEventMessage(new MemberEvent());
        LeafAggregate testModel = new LeafAggregate();

        AggregateModel<LeafAggregate> testSubject =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(LeafAggregate.class);

        testSubject.publish(testAggregateEvent, testModel);
        assertEquals(expectedNumberOfAggregateEventHandlerInvocations, aggregateEventCounter.get());
        testSubject.publish(testMemberEvent, testModel);
        assertEquals(expectedNumberOfMemberEventHandlerInvocations, memberEventCounter.get());
    }

    @Test
    void testCreateAggregateModelDoesNotDuplicateRootLevelAggregateMembersForPolymorphicAggregates() {
        int expectedNumberOfAggregateEventHandlerInvocations = 2;
        int expectedNumberOfMemberEventHandlerInvocations = 1;

        EventMessage<AggregateEvent> testAggregateEvent = GenericEventMessage.asEventMessage(new AggregateEvent());
        EventMessage<MemberEvent> testMemberEvent = GenericEventMessage.asEventMessage(new MemberEvent());
        LeafAggregate testModel = new LeafAggregate();

        //noinspection unchecked
        Set<Class<? extends RootAggregate>> subtypes = Sets.newSet(LeafAggregate.class, OtherLeafAggregate.class);
        AggregateModel<RootAggregate> testSubject =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(RootAggregate.class, subtypes);

        testSubject.publish(testAggregateEvent, testModel);
        assertEquals(expectedNumberOfAggregateEventHandlerInvocations, aggregateEventCounter.get());
        testSubject.publish(testMemberEvent, testModel);
        assertEquals(expectedNumberOfMemberEventHandlerInvocations, memberEventCounter.get());
    }

    @SuppressWarnings("unused")
    private abstract class RootAggregate {

        @AggregateMember
        private final Member member = new Member();

        @EventHandler
        public void on(AggregateEvent event) {
            aggregateEventCounter.incrementAndGet();
        }
    }

    private abstract class NodeAggregate extends RootAggregate {

        @EventHandler
        public void on(AggregateEvent event) {
            aggregateEventCounter.incrementAndGet();
        }
    }

    private class LeafAggregate extends NodeAggregate {

        @EventHandler
        public void on(AggregateEvent event) {
            aggregateEventCounter.incrementAndGet();
        }
    }

    private class OtherLeafAggregate extends NodeAggregate {

        @EventHandler
        public void on(AggregateEvent event) {
            aggregateEventCounter.incrementAndGet();
        }
    }

    @SuppressWarnings("unused")
    private class Member {

        @EventHandler
        public void on(AggregateEvent event) {
            aggregateEventCounter.incrementAndGet();
        }

        @EventHandler
        public void on(MemberEvent event) {
            memberEventCounter.incrementAndGet();
        }
    }

    private static class AggregateEvent {

    }

    private static class MemberEvent {

    }
}
