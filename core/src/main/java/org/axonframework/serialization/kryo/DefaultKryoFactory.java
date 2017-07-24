package org.axonframework.serialization.kryo;

import java.lang.reflect.InvocationHandler;
import java.util.*;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer;
import com.esotericsoftware.kryo.serializers.DefaultSerializers;
import com.esotericsoftware.kryo.serializers.MapSerializer;
import de.javakaffee.kryoserializers.*;
import org.axonframework.messaging.MetaData;
import org.objenesis.strategy.StdInstantiatorStrategy;

/**
 * By default, this implementation:
 * - uses {@link CompatibleFieldSerializer} that makes the serialization forward and backward compatible
 * - uses {@link StdInstantiatorStrategy} that allows deserialization of class without the need to have
 * a no-arg constructor
 * - register multiple serializers to process specific JDK classes
 *
 * @author Reda.Housni-Alaoui
 * @since 3.1
 */
public class DefaultKryoFactory implements KryoFactory {

	@Override
	public Kryo create() {
		Kryo kryo = new Kryo();
		kryo.setDefaultSerializer(CompatibleFieldSerializer.class);
		kryo.setInstantiatorStrategy(new Kryo.DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));

		kryo.register(MetaData.class, new MetaDataSerializer());
		kryo.register(Arrays.asList("").getClass(), new ArraysAsListSerializer());
		kryo.register(Collections.EMPTY_LIST.getClass(), new DefaultSerializers.CollectionsEmptyListSerializer());
		kryo.register(Collections.EMPTY_MAP.getClass(), new DefaultSerializers.CollectionsEmptyMapSerializer());
		kryo.register(Collections.EMPTY_SET.getClass(), new DefaultSerializers.CollectionsEmptySetSerializer());
		kryo.register(Collections.emptySortedSet().getClass(), new CollectionsEmptySortedSetSerializer());

		kryo.register(Collections.singletonList("").getClass(), new DefaultSerializers.CollectionsSingletonListSerializer());
		kryo.register(Collections.singleton("").getClass(), new DefaultSerializers.CollectionsSingletonSetSerializer());
		kryo.register(Collections.singletonMap("", "").getClass(), new DefaultSerializers.CollectionsSingletonMapSerializer());

		kryo.register(GregorianCalendar.class, new GregorianCalendarSerializer());
		kryo.register(InvocationHandler.class, new JdkProxySerializer());

		UnmodifiableCollectionsSerializer.registerSerializers(kryo);
		SynchronizedCollectionsSerializer.registerSerializers(kryo);
		return kryo;
	}

	/**
	 * Kryo EmptySortedSet serializer
	 */
	private static final class CollectionsEmptySortedSetSerializer extends Serializer<SortedSet<?>> {

		@Override
		public void write(Kryo kryo, Output output, SortedSet<?> object) {

		}

		@Override
		public SortedSet<?> read(Kryo kryo, Input input, Class<SortedSet<?>> type) {
			return Collections.emptySortedSet();
		}
	}

	/**
	 * Kryo {@link MetaData} serializer
	 */
	private static final class MetaDataSerializer extends MapSerializer {

		@Override
		protected Map create(Kryo kryo, Input input, Class<Map> type) {
			return new HashMap();
		}

		@SuppressWarnings("unchecked")
		@Override
		public Map read(Kryo kryo, Input input, Class<Map> type) {
			Map map = super.read(kryo, input, type);
			return MetaData.from(map);
		}

		@Override
		protected Map createCopy(Kryo kryo, Map original) {
			return new HashMap();
		}

		@SuppressWarnings("unchecked")
		@Override
		public Map copy(Kryo kryo, Map original) {
			Map map = super.copy(kryo, original);
			return MetaData.from(map);
		}
	}
}
