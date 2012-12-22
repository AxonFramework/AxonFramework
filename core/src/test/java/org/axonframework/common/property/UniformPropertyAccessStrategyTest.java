package org.axonframework.common.property;


import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class UniformPropertyAccessStrategyTest
        extends AbstractPropertyAccessStrategyTest<UniformPropertyAccessStrategyTest.TestMessage> {

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
        return new UniformPropertyAccessStrategy().propertyFor(TestMessage.class, property);
    }

    protected String voidPropertyName() {
        return "voidMethod";
    }

    @SuppressWarnings("UnusedDeclaration")
    static class TestMessage {

        public String actualProperty() {
            return "value";
        }

        public String exceptionProperty() {
            throw new RuntimeException("GetTestException");
        }

        public void voidMethod() {
            fail("This method should never be invoked");
        }
    }
}
