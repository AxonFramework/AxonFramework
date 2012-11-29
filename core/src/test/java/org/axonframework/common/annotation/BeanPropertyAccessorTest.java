package org.axonframework.common.annotation;

public class BeanPropertyAccessorTest extends PropertyAccessorTest {
    class TestMessage extends PropertyAccessorTest.TestMessage {
        private String testProperty = text;

        public String getTestProperty() {
            return testProperty;
        }
    }

    @Override
    protected TestMessage message() {
        return new TestMessage();
    }

    @Override
    protected PropertyAccessor propertyAccessor() {
        return new BeanStylePropertyAccessor();
    }
}
