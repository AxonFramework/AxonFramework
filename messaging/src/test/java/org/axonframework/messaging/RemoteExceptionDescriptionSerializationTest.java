package org.axonframework.messaging;

import static junit.framework.TestCase.assertEquals;

import org.axonframework.serialization.TestSerializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

/**
 * Tests serialization capabilities of {@link RemoteExceptionDescription}.
 * @author JohT
 */
@RunWith(Parameterized.class)
public class RemoteExceptionDescriptionSerializationTest {

    private final TestSerializer serializer;

    public RemoteExceptionDescriptionSerializationTest(TestSerializer serializer) {
        this.serializer = serializer;
    }

    @Parameterized.Parameters(name = "{index} {0}")
    public static Collection<TestSerializer> serializers() {
       return TestSerializer.all();
    }
    
    @Test
    public void testTokenShouldBeSerializableWithJackson() {
        Throwable cause = new IllegalArgumentException("This is a test");
        Throwable exception = new IllegalStateException("Test with cause", cause);
        RemoteExceptionDescription descriptionToTest = RemoteExceptionDescription.describing(exception);
        assertEquals(descriptionToTest, serializer.serializeDeserialize(descriptionToTest));
    }
}