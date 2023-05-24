package org.axonframework.deadline.dbscheduler;

import com.github.kagkarlsson.scheduler.serializer.GsonSerializer;
import com.github.kagkarlsson.scheduler.serializer.JacksonSerializer;
import com.github.kagkarlsson.scheduler.serializer.JavaSerializer;
import com.github.kagkarlsson.scheduler.serializer.Serializer;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DbSchedulerDeadlineDetailsTest {

    @MethodSource("serializers")
    @ParameterizedTest
    void shouldBeSerializable(Serializer serializer) {
        DbSchedulerDeadlineDetails expected = new DbSchedulerDeadlineDetails(
                "deadlinename",
                "someScope",
                "org.axonframework.modelling.command.AggregateScopeDescriptor",
                "{\"foo\":\"bar\"}",
                "com.someCompany.api.ImportantEvent",
                "1",
                "{\"traceId\":\"1acc25e2-58a1-4dec-8b43-55388188500a\"}"
        );
        byte[] serialized = serializer.serialize(expected);
        DbSchedulerDeadlineDetails result = serializer.deserialize(DbSchedulerDeadlineDetails.class, serialized);
        assertEquals(expected, result);
    }

    public static Collection<Serializer> serializers() {
        List<Serializer> serializers = new ArrayList<>();
        serializers.add(new JavaSerializer());
        serializers.add(new JacksonSerializer());
        serializers.add(new GsonSerializer());
        return serializers;
    }
}
