package org.axonframework.axonserver.connector.util;

import io.axoniq.axonserver.grpc.ErrorMessage;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Author: marc
 */
class ExceptionSerializerTest {
    @Test
    void serializeNullClient() {
        ErrorMessage result = ExceptionSerializer.serialize(null,
                                                            new RuntimeException(
                                                                    "Something went wrong"));
        assertEquals("", result.getLocation());
    }

    @Test
    void serializeNonNullClient() {
        ErrorMessage result = ExceptionSerializer.serialize("Client",
                                                            new RuntimeException(
                                                                    "Something went wrong"));
        assertEquals("Client", result.getLocation());
    }
}