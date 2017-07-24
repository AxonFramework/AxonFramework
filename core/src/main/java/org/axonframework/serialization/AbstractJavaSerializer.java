package org.axonframework.serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.axonframework.common.Assert;

/**
 * Serializer implementation that uses Java-Like serialization to serialize and deserialize object instances.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AbstractJavaSerializer implements Serializer {

	private final Converter converter = new ChainingConverter();
	private final RevisionResolver revisionResolver;

	/**
	 * Initialize the serializer
	 *
	 * @param revisionResolver The revision resolver providing the revision numbers for a given class
	 */
	public AbstractJavaSerializer(RevisionResolver revisionResolver) {
		Assert.notNull(revisionResolver, () -> "revisionResolver may not be null");
		this.revisionResolver = revisionResolver;
	}

	@SuppressWarnings({"NonSerializableObjectPassedToObjectStream", "ThrowFromFinallyBlock"})
	@Override
	public <T> SerializedObject<T> serialize(Object instance, Class<T> expectedType) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			doSerialize(baos, instance);
		} catch (IOException e) {
			throw new SerializationException("An exception occurred writing serialized data to the output stream", e);
		}
		T converted = converter.convert(baos.toByteArray(), expectedType);
		return new SimpleSerializedObject<>(converted, expectedType, instance.getClass().getName(),
				revisionOf(instance.getClass()));
	}

	protected abstract void doSerialize(OutputStream outputStream, Object instance) throws IOException;

	@Override
	public <T> boolean canSerializeTo(Class<T> expectedRepresentation) {
		return converter.canConvert(byte[].class, expectedRepresentation);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <S, T> T deserialize(SerializedObject<S> serializedObject) {
		SerializedObject<InputStream> converted =
				converter.convert(serializedObject, InputStream.class);
		try {
			return doDeserialize(converted.getData());
		} catch (ClassNotFoundException | IOException e) {
			throw new SerializationException("An error occurred while deserializing: " + e.getMessage(), e);
		}
	}

	protected abstract <T> T doDeserialize(InputStream inputStream) throws ClassNotFoundException, IOException;

	@Override
	public Class classForType(SerializedType type) {
		try {
			return Class.forName(type.getName());
		} catch (ClassNotFoundException e) {
			throw new UnknownSerializedTypeException(type, e);
		}
	}

	@Override
	public SerializedType typeForClass(Class type) {
		return new SimpleSerializedType(type.getName(), revisionOf(type));
	}

	@Override
	public Converter getConverter() {
		return converter;
	}

	private String revisionOf(Class<?> type) {
		return revisionResolver.revisionOf(type);
	}
}
