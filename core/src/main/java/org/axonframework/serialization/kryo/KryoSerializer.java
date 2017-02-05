package org.axonframework.serialization.kryo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoPool;
import org.axonframework.serialization.AbstractJavaSerializer;
import org.axonframework.serialization.AnnotationRevisionResolver;
import org.axonframework.serialization.RevisionResolver;

/**
 * Serializer implementation that uses Kryo serialization to serialize and deserialize object instances.
 *
 * @author Reda.Housni-Alaoui
 * @since 3.1
 */
public class KryoSerializer extends AbstractJavaSerializer {

	private final KryoPool kryoPool;

	/**
	 * Initialize the serializer with annotation revision resolver
	 */
	public KryoSerializer() {
		this(new AnnotationRevisionResolver());
	}

	/**
	 * Initialize the serializer
	 *
	 * @param revisionResolver The revision resolver providing the revision numbers for a given class
	 */
	public KryoSerializer(RevisionResolver revisionResolver) {
		this(new DefaultKryoPool(), revisionResolver);
	}

	/**
	 * Initialize the serializer
	 *
	 * @param kryoPool The Kryo pool that will be used to get a Kryo instance
	 */
	public KryoSerializer(KryoPool kryoPool) {
		this(kryoPool, new AnnotationRevisionResolver());
	}

	/**
	 * Initialize the serializer
	 *
	 * @param kryoPool The Kryo pool that will be used to get a Kryo instance
	 * @param revisionResolver The revision resolver to be used
	 */
	public KryoSerializer(KryoPool kryoPool, RevisionResolver revisionResolver) {
		super(revisionResolver);
		this.kryoPool = kryoPool;
	}

	@Override
	protected void doSerialize(OutputStream outputStream, Object instance) throws IOException {
		try (Output output = new Output(outputStream)) {
			kryoPool.run(kryo -> {
				kryo.writeClassAndObject(output, instance);
				return null;
			});
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	protected <T> T doDeserialize(InputStream inputStream) throws ClassNotFoundException, IOException {
		try (Input input = new Input(inputStream)) {
			return (T) kryoPool.run(kryo -> kryo.readClassAndObject(input));
		}
	}
}
