package org.axonframework.common.annotation;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static junit.framework.Assert.assertEquals;

public abstract class PropertyAccessorTest {

    @Test(expected = NoSuchMethodException.class)
    public void testMethodFor_NoMethodForProperty() throws NoSuchMethodException {
        propertyAccessor().methodFor("absentProp", message().getClass());
    }

    @Test
    public void testMethodFor_PresentProperty() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object msg = message();
        Method method = propertyAccessor().methodFor("testProperty", msg.getClass());
        assertEquals(text, method.invoke(msg));
    }

    abstract protected Object message();
    abstract protected PropertyAccessor propertyAccessor();

    protected static String text = "some";
}
