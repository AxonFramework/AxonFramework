package org.axonframework.eventhandling.scheduling.java;

import static junit.framework.TestCase.assertEquals;

import org.axonframework.serialization.TestSerializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

/**
 * Tests serialization capabilities of {@link SimpleScheduleToken}.
 * 
 * @author JohT
 */
@RunWith(Parameterized.class)
public class SimpleScheduleTokenSerializationTest {

    private final TestSerializer serializer;

    public SimpleScheduleTokenSerializationTest(TestSerializer serializer) {
        this.serializer = serializer;
    }
    
    @Parameterized.Parameters(name = "{index} {0}")
    public static Collection<TestSerializer> serializers() {
       return TestSerializer.all();
    }

    @Test
    public void testTokenShouldBeSerializable() {
        SimpleScheduleToken tokenToTest = new SimpleScheduleToken("28bda08d-2dd5-4420-98cb-75ca073446b4");
        assertEquals(tokenToTest, serializer.serializeDeserialize(tokenToTest));
    }
}