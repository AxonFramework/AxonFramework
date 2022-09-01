package org.axonframework.commandhandling.distributed.commandfilter;

import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DenyAll} can be serialized through Axon's {@link
 * org.axonframework.serialization.Serializer} implementations.
 *
 * @author Steven van Beelen
 */
class DenyAllSerializationTest {

    private final DenyAll testSubject = DenyAll.INSTANCE;

    private static Collection<TestSerializer> testSerializers() {
        return TestSerializer.all();
    }

    @ParameterizedTest
    @MethodSource("testSerializers")
    void denyAllFilterShouldBeSerializable(TestSerializer serializer) {
        assertEquals(testSubject, serializer.serializeDeserialize(testSubject));
    }
}