package org.axonframework.common;

public class BeanPropertyAccessorTest extends PropertyAccessorTest {
    class TestMessage {
        private String testProperty = text;

        public String getTestProperty() {
            return testProperty;
        }

        public void setTestProperty(String newValue) {
            testProperty = newValue;
        }

        public String getExcProperty() {
            throw new RuntimeException("GetTestException");
        }

        public void setExcProperty() {
            throw new RuntimeException("SetTestException");
        }
    }

    @Override
    protected TestMessage message() {
        return new TestMessage();
    }

    @Override
    protected PropertyAccessor propertyAccessor(String property) {
        return new BeanStylePropertyAccessor(property);
    }
}
