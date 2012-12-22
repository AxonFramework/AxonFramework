package org.axonframework.common.property;

import org.junit.*;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;


public abstract class AbstractPropertyAccessStrategyTest<T> {

    @Test
    public void testGetValue() {
        final Property<T> actualProperty = getProperty(regularPropertyName());
        assertNotNull(actualProperty);
        assertNotNull(actualProperty.<String>getValue(propertyHoldingInstance()));
    }

    @Test
    public void testGetValue_BogusProperty() {
        assertNull(getProperty(unknownPropertyName()));
    }

    @Test(expected = PropertyAccessException.class)
    public void testGetValue_ExceptionOnAccess() {
        getProperty(exceptionPropertyName()).getValue(propertyHoldingInstance());
    }

    @Test
    public void testVoidReturnTypeRejected() {
        Property property = getProperty(voidPropertyName());
        Assert.assertNull("void methods should not be accepted as property", property);
    }

    protected abstract String voidPropertyName();

    protected abstract String exceptionPropertyName();
    protected abstract String regularPropertyName();
    protected abstract String unknownPropertyName();

    protected abstract T propertyHoldingInstance();

    protected abstract Property<T> getProperty(String property);
}
