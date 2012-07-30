package org.axonframework.saga;

import org.axonframework.domain.EventMessage;
import org.axonframework.saga.annotation.AssociationValuesImpl;
import org.axonframework.testutils.MockException;
import org.junit.*;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * @author Allard Buijze
 */
public class SimpleResourceInjectorTest {

    private SimpleResourceInjector testSubject;

    @Test
    public void testInjectResource() {
        final SomeResource resource = new SomeResource();
        testSubject = new SimpleResourceInjector(resource);
        final StubSaga saga = new StubSaga();
        testSubject.injectResources(saga);

        assertNull(saga. getSomeWeirdResource());
        assertSame(resource, saga.getSomeResource());
    }

    @Test
    public void testInjectResource_ExceptionsIgnored() {
        final SomeResource resource = new SomeResource();
        testSubject = new SimpleResourceInjector(resource, new SomeWeirdResource());
        final StubSaga saga = new StubSaga();
        testSubject.injectResources(saga);

        assertNull(saga. getSomeWeirdResource());
        assertSame(resource, saga.getSomeResource());
    }


    private static class StubSaga implements Saga {

        private SomeResource someResource;
        private SomeWeirdResource someWeirdResource;

        @Override
        public String getSagaIdentifier() {
            return "id";
        }

        @Override
        public AssociationValues getAssociationValues() {
            return new AssociationValuesImpl();
        }

        @Override
        public void handle(EventMessage event) {
        }

        @Override
        public boolean isActive() {
            return true;
        }

        public void setSomeResource(SomeResource someResource) {
            this.someResource = someResource;
        }

        public SomeResource getSomeResource() {
            return someResource;
        }

        public SomeWeirdResource getSomeWeirdResource() {
            return someWeirdResource;
        }

        public void setSomeWeirdResource(SomeWeirdResource someWeirdResource) {
            throw new MockException();
        }
    }

    private static class SomeWeirdResource {

    }
    private static class SomeResource {

    }
}
