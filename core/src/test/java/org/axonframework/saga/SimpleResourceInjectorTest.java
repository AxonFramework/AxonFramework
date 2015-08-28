/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.saga;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.saga.annotation.AssociationValuesImpl;
import org.axonframework.testutils.MockException;
import org.junit.Test;

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
