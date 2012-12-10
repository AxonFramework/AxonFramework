package org.axonframework.common;

import org.junit.Test;

import static junit.framework.Assert.*;


public class PropertyAccessorFactoryTest {

    @Test
    public void testCreateFor() {
        assertEquals(
                BeanStylePropertyAccessor.class,
                PropertyAccessorFactory.createFor("testProperty").getClass()
        );
    }

}
