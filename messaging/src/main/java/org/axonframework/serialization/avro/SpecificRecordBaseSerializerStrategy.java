package org.axonframework.serialization.avro;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.SchemaStore;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecordBase;
import org.axonframework.serialization.*;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Avro serializer strategy responsible for operations on <code>SpecificRecordBase</code>.
 */
public class SpecificRecordBaseSerializerStrategy implements AvroSerializerStrategy {

  private final SchemaStore schemaStore;
  private final RevisionResolver revisionResolver;

  public SpecificRecordBaseSerializerStrategy(
      SchemaStore schemaStore,
      RevisionResolver revisionResolver
  ) {
    this.schemaStore = schemaStore;
    this.revisionResolver = revisionResolver;
  }

  @Override
  @Nonnull
  public SerializedObject<byte[]> serializeToSingleObjectEncoded(@Nonnull Object object) {
    if (!(object instanceof SpecificRecordBase)) {
      throw new SerializationException("Expected object to be instance of SpecificRecordBase but it was " + object.getClass().getCanonicalName());
    }

    SpecificRecordBase record = (SpecificRecordBase) object;
    BinaryMessageEncoder<SpecificRecordBase> encoder = new BinaryMessageEncoder<>(record.getSpecificData(), record.getSchema());
    final byte[] bytes;
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      encoder.encode(record, outputStream);
      bytes = outputStream.toByteArray();
    } catch (IOException e) {
      throw new SerializationException("Failed to serialize specific record", e);
    }

    return new SimpleSerializedObject<>(bytes, byte[].class, new SimpleSerializedType(
        object.getClass().getCanonicalName(),
        revisionResolver.revisionOf(object.getClass())
    ));
  }

  @Override
  @Nonnull
  public <T> T deserializeFromSingleObjectEncoded(@Nonnull SerializedObject<byte[]> serializedObject, @Nonnull Class<T> readerType) {
    if (!SpecificRecordBase.class.isAssignableFrom(readerType)) {
      throw new SerializationException("Expected reader type to be assignable from SpecificRecordBase but it was " + readerType.getCanonicalName());
    }
    @SuppressWarnings("unchecked")
    Class<SpecificRecordBase> specificRecordBaseClass = (Class<SpecificRecordBase>) readerType;

    long fingerprint = AvroUtil.fingerprint(serializedObject.getData());
    Schema writerSchema = schemaStore.findByFingerprint(fingerprint);
    SpecificData writerSpecificData = SpecificData.getForSchema(writerSchema);
    Class<?> writerClass = writerSpecificData.getClass();
    if (!readerType.isAssignableFrom(writerClass)) {
      throw new SerializationException("Inconsistent type provided for deserialization, expected "
          + readerType.getCanonicalName()
          + " but the bytes were serialized using "
          + writerClass.getCanonicalName()
      );
    }

    SpecificData readerSpecificData = SpecificData.getForClass(specificRecordBaseClass);
    Schema readerSchema = AvroUtil.getSchemaFromSpecificRecordBase(specificRecordBaseClass);

    // TODO: check reader and writer compatibility here

    BinaryMessageDecoder<SpecificRecordBase> decoder = new BinaryMessageDecoder<SpecificRecordBase>(readerSpecificData, readerSchema);
    decoder.addSchema(writerSchema);

    try {
      //noinspection unchecked
      return (T) decoder.decode(serializedObject.getData());
    } catch (IOException e) {
      throw new SerializationException("Failed to deserialize specific record", e);
    }
  }

  @Override
  @Nonnull
  public <T> T deserializeFromGenericRecord(@Nonnull SerializedObject<GenericRecord> serializedObject, @Nonnull Class<T> readerType) {
    if (!SpecificRecordBase.class.isAssignableFrom(readerType)) {
      throw new SerializationException("Expected reader type to be assignable from SpecificRecordBase but it was " + readerType.getCanonicalName());
    }

    Schema writerSchema = serializedObject.getData().getSchema();
//            // TODO if caller does not provide expected type, we use the writer schema to derive the class ... this could be wrong.
//            val readerClass: Class<*> = AvroKotlin.specificData.getClass(writerSchema)

    SpecificData readerSpecificData = SpecificData.getForClass(readerType);
    SpecificRecordBase decoded = (SpecificRecordBase) readerSpecificData.deepCopy(writerSchema, serializedObject.getData());
    //noinspection unchecked
    return (T) decoded;
  }

  @Override
  public boolean test(@Nonnull Class<?> payloadType) {
    return SpecificRecordBase.class.isAssignableFrom(payloadType);
  }

}
