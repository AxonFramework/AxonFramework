package org.axonframework.common.property;

import org.junit.*;

import static junit.framework.Assert.assertEquals;

public class PropertyAccessStrategyTest {

    private PropertyAccessStrategy mock1 = new StubPropertyAccessStrategy(1000, "mock1");
    private PropertyAccessStrategy mock2 = new StubPropertyAccessStrategy(1200, "mock2");
    private PropertyAccessStrategy mock3 = new StubPropertyAccessStrategy(1000, "mock3");
    private PropertyAccessStrategy mock4 = new StubPropertyAccessStrategy(1000, "mock4");
    private TestPropertyAccessStrategy testPropertyAccessStrategy = new TestPropertyAccessStrategy();

    @After
    public void setUp() throws Exception {
        PropertyAccessStrategy.unregister(mock1);
        PropertyAccessStrategy.unregister(mock2);
        PropertyAccessStrategy.unregister(mock3);
        PropertyAccessStrategy.unregister(mock4);
        PropertyAccessStrategy.unregister(testPropertyAccessStrategy);
    }

    @Test
    public void test_BeanPropertyAccess() {
        assertEquals("beanProperty", PropertyAccessStrategy.getProperty(Bean.class, "beanProperty")
                                                           .getValue(new Bean()));
    }

    @Test
    public void test_UniformPropertyAccess() {
        assertEquals("uniformProperty", PropertyAccessStrategy.getProperty(Bean.class, "uniformProperty").getValue(
                new Bean()));
    }

    @Test
    public void test_Register() {
        PropertyAccessStrategy.register(testPropertyAccessStrategy);
        assertEquals("testGetterInvoked",
                     PropertyAccessStrategy.getProperty(Bean.class, "testProperty").getValue(new Bean()));
    }

    @Test
    public void testInvocationOrdering() {
        PropertyAccessStrategy.register(mock1);
        PropertyAccessStrategy.register(mock2);
        assertEquals("mock2",
                     PropertyAccessStrategy.getProperty(Bean.class, "testProperty").getValue(new Bean()));
    }

    @Test
    public void testInvocationOrdering_EqualPriorityUsesClassName() {
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
        protected <T> Property<T> propertyFor(Class<T> targetClass, String property) {
            return new StubProperty<T>("testGetterInvoked");
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
        protected <T> Property<T> propertyFor(Class<T> targetClass, String property) {
            return new TestPropertyAccessStrategy.StubProperty<T>(value);
        }
    }
}

