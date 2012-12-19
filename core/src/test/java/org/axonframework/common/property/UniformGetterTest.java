package org.axonframework.common.property;


public class UniformGetterTest extends GetterTest<UniformGetterTest.TestMessage> {
    static class TestMessage {
        private String testProperty = text;

        public String testProperty() {
            return testProperty;
        }

        public String excProperty() {
            throw new RuntimeException("GetTestException");
        }
    }

    @Override
    protected TestMessage message() {
        return new TestMessage();
    }

    @Override
    protected Getter getter(String property) {
        return new UniformPropertyAccessStrategy().getterFor(TestMessage.class, property);
    }
}
