package org.axonframework.queryhandling.benchmark.misc;

import demo.DemoEvent;
import org.axonframework.queryhandling.benchmark.AbstractBenchmarkTest;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

/*
 * Benchmark                                Mode  Cnt        Score   Error  Units
 * SerializerBenchmark.jacksonSerializer   thrpt    2  2353463,363          ops/s
 * SerializerBenchmark.xStreamsSerializer  thrpt    2   211090,097          ops/s
 */
@State(Scope.Thread)
public class SerializerBenchmark extends AbstractBenchmarkTest {

    private Serializer jacksonSerializer = JacksonSerializer.defaultSerializer();

    private Serializer xStreamsSerializer = XStreamSerializer.defaultSerializer();

    private DemoEvent createEvent() {
        return new DemoEvent(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString()
        );
    }

    @Benchmark
    public void jacksonSerializer() {
        DemoEvent originalDemoEvent = createEvent();
        SerializedObject<byte[]> serializedObject = jacksonSerializer.serialize(originalDemoEvent, byte[].class);

        DemoEvent convertedDemoEvent = jacksonSerializer.deserialize(serializedObject);
        assertEquals(originalDemoEvent, convertedDemoEvent);
    }

    @Benchmark
    public void xStreamsSerializer() {
        DemoEvent originalDemoEvent = createEvent();
        SerializedObject<byte[]> serializedObject = xStreamsSerializer.serialize(originalDemoEvent, byte[].class);

        DemoEvent convertedDemoEvent = xStreamsSerializer.deserialize(serializedObject);
        assertEquals(originalDemoEvent, convertedDemoEvent);
    }
}
