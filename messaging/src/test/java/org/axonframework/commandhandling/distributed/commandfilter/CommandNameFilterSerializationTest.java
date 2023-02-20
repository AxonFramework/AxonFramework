package org.axonframework.commandhandling.distributed.commandfilter;

import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.mockito.internal.util.collections.*;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link CommandNameFilter} can be serialized through Axon's {@link
 * org.axonframework.serialization.Serializer} implementations.
 *
 * @author Steven van Beelen
 */
class CommandNameFilterSerializationTest {

    private final CommandNameFilter testSubject = new CommandNameFilter(Sets.newSet("firstName", "secondName"));

    private static Collection<TestSerializer> testSerializers() {
        return TestSerializer.all();
    }

    @ParameterizedTest
    @MethodSource("testSerializers")
    void commandNameFilterShouldBeSerializable(TestSerializer serializer) {
        assertEquals(testSubject, serializer.serializeDeserialize(testSubject));
    }
}