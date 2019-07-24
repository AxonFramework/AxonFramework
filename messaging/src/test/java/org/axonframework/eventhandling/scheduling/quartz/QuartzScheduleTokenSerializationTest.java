package org.axonframework.eventhandling.scheduling.quartz;

import static junit.framework.TestCase.assertEquals;

import org.axonframework.serialization.TestSerializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

/**
 * Tests serialization capabilities of {@link QuartzScheduleToken}.
 * 
 * @author JohT
 */
@RunWith(Parameterized.class)
public class QuartzScheduleTokenSerializationTest {

    private final TestSerializer serializer;

    public QuartzScheduleTokenSerializationTest(TestSerializer serializer) {
        this.serializer = serializer;
    }

    @Parameterized.Parameters(name = "{index} {0}")
    public static Collection<TestSerializer> serializers() {
       return TestSerializer.all();
    }
    
    @Test
    public void testTokenShouldBeSerializable() {
        QuartzScheduleToken tokenToTest = new QuartzScheduleToken("jobIdentifier", "groupIdentifier");
        assertEquals(tokenToTest, serializer.serializeDeserialize(tokenToTest));
    }
}