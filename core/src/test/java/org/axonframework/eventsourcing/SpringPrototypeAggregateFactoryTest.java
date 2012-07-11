package org.axonframework.eventsourcing;

import org.axonframework.domain.GenericDomainEventMessage;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
@ContextConfiguration(locations = {"/META-INF/spring/spring-prototype-aggregate-factory.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
public class SpringPrototypeAggregateFactoryTest {

    @Autowired
    private SpringPrototypeAggregateFactory<SpringWiredAggregate> testSubject;

    @Test
    public void testContextStarts() throws Exception {
        assertNotNull(testSubject);
    }

    @Test
    public void testCreateNewAggregateInstance() {
        SpringWiredAggregate aggregate = testSubject.createAggregate("id2", new GenericDomainEventMessage<String>(
                "id2", 0, "FirstEvent"));
        assertTrue("Aggregate's init method not invoked", aggregate.isInitialized());
        assertNotNull("ContextAware method not invoked", aggregate.getContext());
        assertEquals("it's here", aggregate.getSpringConfiguredName());
    }

    @Test
    public void testProcessSnapshotAggregateInstance() {
        SpringWiredAggregate aggregate = testSubject.createAggregate("id2",
                                                                     new GenericDomainEventMessage<SpringWiredAggregate>(
                                                                             "id2", 5, new SpringWiredAggregate()));
        assertTrue("Aggregate's init method not invoked", aggregate.isInitialized());
        assertNotNull("ContextAware method not invoked", aggregate.getContext());
        assertEquals("it's here", aggregate.getSpringConfiguredName());
    }
}
