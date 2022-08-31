/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.common.property;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


class PropertyAccessStrategyTest {

    private PropertyAccessStrategy mock1 = new StubPropertyAccessStrategy(1000, "mock1");
    private PropertyAccessStrategy mock2 = new StubPropertyAccessStrategy(1200, "mock2");
    private PropertyAccessStrategy mock3 = new StubPropertyAccessStrategy(1000, "mock3");
    private PropertyAccessStrategy mock4 = new StubPropertyAccessStrategy(1000, "mock4");
    private TestPropertyAccessStrategy testPropertyAccessStrategy = new TestPropertyAccessStrategy();

    @AfterEach
    void setUp() {
        PropertyAccessStrategy.unregister(mock1);
        PropertyAccessStrategy.unregister(mock2);
        PropertyAccessStrategy.unregister(mock3);
        PropertyAccessStrategy.unregister(mock4);
        PropertyAccessStrategy.unregister(testPropertyAccessStrategy);
    }

    @Test
    void beanPropertyAccess() {
        assertEquals("beanProperty", PropertyAccessStrategy.getProperty(Bean.class, "beanProperty")
                                                           .getValue(new Bean()));
    }

    @Test
    void uniformPropertyAccess() {
        assertEquals("uniformProperty", PropertyAccessStrategy.getProperty(Bean.class, "uniformProperty").getValue(
                new Bean()));
    }

    @Test
    void register() {
        PropertyAccessStrategy.register(testPropertyAccessStrategy);
        assertEquals("testGetterInvoked",
                     PropertyAccessStrategy.getProperty(Bean.class, "testProperty").getValue(new Bean()));
    }

    @Test
    void invocationOrdering() {
        PropertyAccessStrategy.register(mock1);
        PropertyAccessStrategy.register(mock2);
        assertEquals("mock2",
                     PropertyAccessStrategy.getProperty(Bean.class, "testProperty").getValue(new Bean()));
    }

    @Test
    void invocationOrdering_EqualPriorityUsesClassName() {
        PropertyAccessStrategy.register(mock3);
        PropertyAccessStrategy.register(mock4);
        assertEquals("mock3",
                     PropertyAccessStrategy.getProperty(Bean.class, "testProperty").getValue(new Bean()));
    }

    static class TestPropertyAccessStrategy extends PropertyAccessStrategy {

        @Override
        protected int getPriority() {
            return Integer.MAX_VALUE;
        }

        @Override
        protected <T> Property<T> propertyFor(Class<? extends T> targetClass, String property) {
            return new StubProperty<>("testGetterInvoked");
        }

        private static class StubProperty<T> implements Property<T> {

            private final String value;

            private StubProperty(String value) {
                this.value = value;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <V> V getValue(T target) {
                return (V) value;
            }
        }
    }

    static class Bean {

        private String beanProperty = "beanProperty";
        private String uniformProperty = "uniformProperty";

        public String getBeanProperty() {
            return beanProperty;
        }

        public String uniformProperty() {
            return uniformProperty;
        }
    }

    private static class StubPropertyAccessStrategy extends PropertyAccessStrategy {

        private final int priority;
        private final String value;

        public StubPropertyAccessStrategy(int priority, String value) {
            this.priority = priority;
            this.value = value;
        }

        @Override
        protected int getPriority() {
            return priority;
        }

        @Override
        protected <T> Property<T> propertyFor(Class<? extends T> targetClass, String property) {
            return new TestPropertyAccessStrategy.StubProperty<>(value);
        }
    }
}

