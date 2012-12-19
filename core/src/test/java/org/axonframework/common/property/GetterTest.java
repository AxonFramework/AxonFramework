package org.axonframework.common.property;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;


public abstract class GetterTest<T> {

    @Test
    public void testGetValue() {
        String value = getter("testProperty").getValue(message());
        assertEquals(text, value);
    }

    public void testGetValue_BogusProperty() {
        assertNull(getter("bogusProperty"));
    }

    @Test(expected = PropertyAccessException.class)
    public void testGetValue_ExceptionOnAccess() {
        String value = getter("excProperty").getValue(message());
    }

    abstract protected T message();
    abstract protected Getter getter(String property);

    protected static String text = "some";
}
