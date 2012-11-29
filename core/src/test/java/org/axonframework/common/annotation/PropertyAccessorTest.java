package org.axonframework.common.annotation;

import org.axonframework.common.annotation.PropertyAccessor;
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
        TestMessage msg = message();
        Method method = propertyAccessor().methodFor("testProperty", msg.getClass());
        assertEquals(TestMessage.text, method.invoke(msg));
    }

    abstract protected <T extends TestMessage> T message();
    abstract protected PropertyAccessor propertyAccessor();

    protected static class TestMessage {
        protected static String text = "some";
    }
}
