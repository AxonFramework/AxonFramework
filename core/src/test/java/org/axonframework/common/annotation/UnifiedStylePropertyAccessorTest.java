package org.axonframework.common.annotation;

public class UnifiedStylePropertyAccessorTest extends PropertyAccessorTest {
    static class TestMessage extends PropertyAccessorTest.TestMessage {
        private String testProperty = text;

        public String testProperty() {
            return testProperty;
        }
    }
    @Override
    protected TestMessage message() {
        return new TestMessage();
    }

    @Override
    protected PropertyAccessor propertyAccessor() {
        return new UnifiedStylePropertyAccessor();
    }
}
