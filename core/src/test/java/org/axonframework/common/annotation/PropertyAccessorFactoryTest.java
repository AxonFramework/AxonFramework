package org.axonframework.common.annotation;

import org.axonframework.common.AxonConfigurationException;
import org.junit.Test;

import static junit.framework.Assert.*;

public class PropertyAccessorFactoryTest {

    @Test
    public void testCreateFor_SuccessfullCreation() {
        assertEquals(
                BeanStylePropertyAccessor.class,
                PropertyAccessorFactory.createFor(BeanStylePropertyAccessor.class).getClass()
        );
    }

    @Test
    public void testCreateFor_NoArgConstructorAbsent() {
        try {
            PropertyAccessorFactory.createFor(WrongPropertyAccessor.class);
        } catch (AxonConfigurationException ace) {
            assertTrue(ace.getMessage().contains("WrongPropertyAccessor") &&
                    ace.getMessage().contains("Check that it has no-arg constructor"));
        }
    }

    static class WrongPropertyAccessor extends AbstractPropertyAccessor {
        WrongPropertyAccessor(String iNeedSmth) {

        }

        @Override
        public String methodName(String propertyName) {
            return propertyName;
        }
    }
}
