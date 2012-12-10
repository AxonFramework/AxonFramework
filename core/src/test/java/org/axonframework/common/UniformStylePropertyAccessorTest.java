package org.axonframework.common;


public class UniformStylePropertyAccessorTest extends PropertyAccessorTest {
    static class TestMessage {
        private String testProperty = text;

        public String testProperty() {
            return testProperty;
        }

        public void testProperty_$eq(String newValue) {
            testProperty = newValue;
        }

        public String excProperty() {
            throw new RuntimeException("GetTestException");
        }

        public void excProperty_$eq() {
            throw new RuntimeException("SetTestException");
        }
    }

    @Override
    protected TestMessage message() {
        return new TestMessage();
    }

    @Override
    protected PropertyAccessor propertyAccessor(String property) {
        return new UniformStylePropertyAccessor(property);
    }
}
