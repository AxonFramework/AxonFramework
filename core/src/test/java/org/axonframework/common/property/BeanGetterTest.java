package org.axonframework.common.property;

public class BeanGetterTest extends GetterTest<BeanGetterTest.TestMessage> {
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
    }

    @Override
    protected TestMessage message() {
        return new TestMessage();
    }

    @Override
    protected Getter getter(String property) {
        return new BeanPropertyAccessStrategy().getterFor(TestMessage.class, property);
    }
}
