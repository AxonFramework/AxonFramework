package org.axonframework.common.property;

import org.junit.Test;

import static junit.framework.Assert.*;

public class PropertyAccessStrategyTest {
    @Test
    public void test_BeanPropertyAccess() {
        assertEquals("beanProperty", PropertyAccessStrategy.getter(Bean.class, "beanProperty").getValue(new Bean()));
    }

    @Test
    public void test_UniformPropertyAccess() {
        assertEquals("uniformProperty", PropertyAccessStrategy.getter(Bean.class, "uniformProperty").getValue(new Bean()));
    }

    @Test
    public void test_Register() {
        PropertyAccessStrategy.register(new TestPropertyAccessStrategy());
        assertEquals(
                "testGetterInvoked",
                PropertyAccessStrategy.getter(Bean.class, "testProperty").getValue(new Bean()));
    }

    static class TestPropertyAccessStrategy extends PropertyAccessStrategy {
        @Override
        protected Getter getterFor(Class<?> targetClass, String property) {
            return new Getter() {
                @Override
                @SuppressWarnings("unchecked")
                public <V> V getValue(Object target) {
                    return (V) "testGetterInvoked";
                }
            };
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
}

