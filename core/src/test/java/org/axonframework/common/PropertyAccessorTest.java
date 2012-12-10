package org.axonframework.common;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;


public abstract class PropertyAccessorTest {

    @Test
    public void testGetValue() {
        String value = propertyAccessor("testProperty").getValue(message());
        assertEquals(text, value);
    }

    @Test(expected = PropertyAccessException.class)
    public void testGetValue_BogusProperty() {
        propertyAccessor("bogusProperty").getValue(message());
    }

    @Test(expected = PropertyAccessException.class)
    public void testGetValue_ExceptionOnAccess() {
        propertyAccessor("excProperty").getValue(message());
    }

    @Test
    public void testSetValue() {
        Object message = message();
        PropertyAccessor accessor = propertyAccessor("testProperty");
        accessor.setValue("new", message);
        String value = accessor.getValue(message);
        assertEquals("new", value);
    }

    @Test(expected = PropertyAccessException.class)
    public void testSetValue_BogusProperty() {
        propertyAccessor("bogusProperty").setValue("bogusValue", message());
    }

    @Test(expected = PropertyAccessException.class)
    public void testSetValue_ExceptionOnAccess() {
        propertyAccessor("excProperty").setValue("value", message());
    }

    abstract protected Object message();
    abstract protected PropertyAccessor propertyAccessor(String property);

    protected static String text = "some";
}
