package org.axonframework.axonserver.connector;

import io.axoniq.axonserver.grpc.control.PlatformInboundInstruction;
import org.junit.jupiter.api.*;

import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GrpcInstructionResultPublisher}
 *
 * @author Sara Pellegrini
 */
class GrpcInstructionResultPublisherTest {

    private final List<PlatformInboundInstruction> published = new LinkedList<>();

    private GrpcInstructionResultPublisher testSubject = new GrpcInstructionResultPublisher(() -> "myClient",
                                                                                            published::add);

    @BeforeEach
    void setUp() {
        published.clear();
    }

    @Test
    void publishSuccessFor() {
        assertTrue(published.isEmpty());
        testSubject.publishSuccessFor("successId");
        PlatformInboundInstruction result = published.get(0);
        assertEquals("successId", result.getResult().getInstructionId());
        assertTrue(result.getResult().getSuccess());
        assertFalse(result.getResult().hasError());
    }

    @Test
    void publishFailureFor() {
        assertTrue(published.isEmpty());
        testSubject.publishFailureFor("failureId", new RuntimeException("MyError"));
        PlatformInboundInstruction result = published.get(0);
        assertEquals("failureId", result.getResult().getInstructionId());
        assertFalse(result.getResult().getSuccess());
        assertTrue(result.getResult().hasError());
        assertEquals("MyError", result.getResult().getError().getMessage());
        assertEquals("AXONIQ-1004", result.getResult().getError().getErrorCode());
    }
}