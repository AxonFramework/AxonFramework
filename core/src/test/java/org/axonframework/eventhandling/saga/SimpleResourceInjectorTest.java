/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.saga;

import org.axonframework.common.MockException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.junit.Test;

import javax.inject.Inject;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * @author Allard Buijze
 */
public class SimpleResourceInjectorTest {

    private SimpleResourceInjector testSubject;

    @Test
    public void testInjectFieldResource() throws Exception {
        SomeFieldResource expectedFieldResource = new SomeFieldResource();
        testSubject = new SimpleResourceInjector(expectedFieldResource);
        final StubSaga saga = new StubSaga();
        testSubject.injectResources(saga);

        assertNull(saga.getSomeWeirdResource());
        assertSame(expectedFieldResource, saga.getSomeFieldResource());
    }

    @Test
    public void testInjectMethodResource() {
        final SomeMethodResource expectedMethodResource = new SomeMethodResource();
        testSubject = new SimpleResourceInjector(expectedMethodResource);
        final StubSaga saga = new StubSaga();
        testSubject.injectResources(saga);

        assertNull(saga.getSomeWeirdResource());
        assertSame(expectedMethodResource, saga.getSomeMethodResource());
    }

    @Test
    public void testInjectFieldAndMethodResources() throws Exception {
        final SomeFieldResource expectedFieldResource = new SomeFieldResource();
        final SomeMethodResource expectedMethodResource = new SomeMethodResource();
        testSubject = new SimpleResourceInjector(expectedFieldResource, expectedMethodResource);
        final StubSaga saga = new StubSaga();
        testSubject.injectResources(saga);

        assertNull(saga.getSomeWeirdResource());
        assertSame(expectedFieldResource, saga.getSomeFieldResource());
        assertSame(expectedMethodResource, saga.getSomeMethodResource());
    }

    @Test
    public void testInjectResource_ExceptionsIgnored() {
        final SomeMethodResource resource = new SomeMethodResource();
        testSubject = new SimpleResourceInjector(resource, new SomeWeirdResource());
        final StubSaga saga = new StubSaga();
        testSubject.injectResources(saga);

        assertNull(saga.getSomeWeirdResource());
        assertSame(resource, saga.getSomeMethodResource());
    }

    private static class StubSaga implements Saga<StubSaga> {

        @Inject
        private SomeFieldResource someFieldResource;
        private SomeMethodResource someMethodResource;
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
        public <R> R invoke(Function<StubSaga, R> invocation) {
            return invocation.apply(this);
        }

        @Override
        public void execute(Consumer<StubSaga> invocation) {
            invocation.accept(this);
        }

        @Override
        public boolean handle(EventMessage event) {
            return true;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public TrackingToken trackingToken() {
            return null;
        }

        public SomeFieldResource getSomeFieldResource() {
            return someFieldResource;
        }

        public SomeMethodResource getSomeMethodResource() {
            return someMethodResource;
        }

        @Inject
        public void setSomeMethodResource(SomeMethodResource someMethodResource) {
            this.someMethodResource = someMethodResource;
        }

        public SomeWeirdResource getSomeWeirdResource() {
            return someWeirdResource;
        }

        public void setSomeWeirdResource(SomeWeirdResource someWeirdResource) {
            throw new MockException();
        }

    }

    private static class SomeFieldResource {
    }

    private static class SomeMethodResource {
    }

    private static class SomeWeirdResource {
    }

}
