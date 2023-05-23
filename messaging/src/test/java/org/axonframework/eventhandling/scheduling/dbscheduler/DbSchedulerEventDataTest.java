package org.axonframework.eventhandling.scheduling.dbscheduler;

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

class DbSchedulerEventDataTest {

    @MethodSource("serializers")
    @ParameterizedTest
    void shouldBeSerializable(Serializer serializer) {
        DbSchedulerEventData expected = new DbSchedulerEventData(
                "payload",
                "class",
                "0",
                "{\"foo\":\"bar\"}"
        );
        byte[] serialized = serializer.serialize(expected);
        DbSchedulerEventData result = serializer.deserialize(DbSchedulerEventData.class, serialized);
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
