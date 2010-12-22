/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.saga;

import org.axonframework.domain.Event;
import org.axonframework.saga.annotation.AssociationValuesImpl;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class GenericSagaFactoryTest {

    private GenericSagaFactory testSubject;

    @Before
    public void setUp() throws Exception {
        this.testSubject = new GenericSagaFactory();
    }

    @Test
    public void testSupports() {
        assertTrue(testSubject.supports(SupportedSaga.class));
        assertFalse(testSubject.supports(UnsupportedSaga.class));
        assertFalse(testSubject.supports(PrivateConstructorSaga.class));
    }

    @Test
    public void testCreateInstance_Supported() {
        assertNotNull(testSubject.createSaga(SupportedSaga.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateInstance_Unsupported() {
        testSubject.createSaga(UnsupportedSaga.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateInstance_PrivateConstructor() {
        testSubject.createSaga(PrivateConstructorSaga.class);
    }

    public static class SupportedSaga implements Saga {

        @Override
        public String getSagaIdentifier() {
            return "supported";
        }

        @Override
        public AssociationValues getAssociationValues() {
            return new AssociationValuesImpl();
        }

        @Override
        public void handle(Event event) {
        }

        @Override
        public boolean isActive() {
            return true;
        }
    }

    public static class UnsupportedSaga implements Saga {

        private final String identifier;

        public UnsupportedSaga(String identifier) {
            this.identifier = identifier;
        }

        @Override
        public String getSagaIdentifier() {
            return identifier;
        }

        @Override
        public AssociationValues getAssociationValues() {
            return new AssociationValuesImpl();
        }

        @Override
        public void handle(Event event) {
        }

        @Override
        public boolean isActive() {
            return true;
        }
    }

    public static class PrivateConstructorSaga implements Saga {

        private PrivateConstructorSaga() {
        }

        @Override
        public String getSagaIdentifier() {
            return "privateConstructor";
        }

        @Override
        public AssociationValues getAssociationValues() {
            return new AssociationValuesImpl();
        }

        @Override
        public void handle(Event event) {
        }

        @Override
        public boolean isActive() {
            return true;
        }
    }
}
