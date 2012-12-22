package org.axonframework.common.property;

import static org.junit.Assert.fail;

public class BeanPropertyAccessStrategyTest extends
        AbstractPropertyAccessStrategyTest<BeanPropertyAccessStrategyTest.TestMessage> {

    @Override
    protected String exceptionPropertyName() {
        return "exceptionProperty";
    }

    @Override
    protected String regularPropertyName() {
        return "actualProperty";
    }

    @Override
    protected String unknownPropertyName() {
        return "bogusProperty";
    }

    @Override
    protected TestMessage propertyHoldingInstance() {
        return new TestMessage();
    }

    @Override
    protected Property<TestMessage> getProperty(String property) {
        return new BeanPropertyAccessStrategy().propertyFor(TestMessage.class, property);
    }

    protected String voidPropertyName() {
        return "voidMethod";
    }

    @SuppressWarnings("UnusedDeclaration")
    class TestMessage {

        public String getActualProperty() {
            return "propertyValue";
        }

        public String getExceptionProperty() {
            throw new RuntimeException("GetTestException");
        }

        public void getVoidMethod() {
            fail("This method should never be invoked");
        }
    }
}
