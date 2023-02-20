package org.axonframework.commandhandling.distributed.commandfilter;

import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link OrCommandMessageFilter} can be serialized through Axon's {@link
 * org.axonframework.serialization.Serializer} implementations.
 *
 * @author Steven van Beelen
 */
class OrCommandMessageFilterSerializationTest {

    private final OrCommandMessageFilter testSubject =
            new OrCommandMessageFilter(new CommandNameFilter("firstName"), new CommandNameFilter("secondName"));

    private static Collection<TestSerializer> testSerializers() {
        return TestSerializer.all();
    }

    @ParameterizedTest
    @MethodSource("testSerializers")
    void orCommandMessageFilterShouldBeSerializable(TestSerializer serializer) {
        assertEquals(testSubject, serializer.serializeDeserialize(testSubject));
    }
}