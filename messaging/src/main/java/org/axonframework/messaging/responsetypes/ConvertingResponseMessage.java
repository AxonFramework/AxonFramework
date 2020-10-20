package org.axonframework.messaging.responsetypes;

import org.axonframework.messaging.IllegalPayloadAccessException;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.util.Map;
import java.util.Optional;

/**
 * Implementation of a QueryResponseMessage that is aware of the requested response type and performs a just-in-time
 * conversion to ensure the response is formatted as requested.
 * <p>
 * The conversion is generally used to accommodate response types that aren't compatible with serialization, such as
 * {@link OptionalResponseType}.
 *
 * @param <R> the type of response expected
 * @author Allard Buijze
 * @since 4.3
 */
public class ConvertingResponseMessage<R> implements QueryResponseMessage<R> {

    private final ResponseType<R> expectedResponseType;
    private final QueryResponseMessage<?> responseMessage;

    /**
     * Initialize a response message, using {@code expectedResponseType} to convert the payload from the {@code
     * responseMessage}, if necessary.
     *
     * @param expectedResponseType an instance describing the expected response type
     * @param responseMessage      the message containing the actual response from the handler
     */
    public ConvertingResponseMessage(ResponseType<R> expectedResponseType,
                                     QueryResponseMessage<?> responseMessage) {
        this.expectedResponseType = expectedResponseType;
        this.responseMessage = responseMessage;
    }

    @Override
    public <S> SerializedObject<S> serializePayload(Serializer serializer, Class<S> expectedRepresentation) {
        return responseMessage.serializePayload(serializer, expectedRepresentation);
    }

    @Override
    public <T> SerializedObject<T> serializeExceptionResult(Serializer serializer, Class<T> expectedRepresentation) {
        return responseMessage.serializeExceptionResult(serializer, expectedRepresentation);
    }

    @Override
    public <R1> SerializedObject<R1> serializeMetaData(Serializer serializer, Class<R1> expectedRepresentation) {
        return responseMessage.serializeMetaData(serializer, expectedRepresentation);
    }

    @Override
    public boolean isExceptional() {
        return responseMessage.isExceptional();
    }

    @Override
    public Optional<Throwable> optionalExceptionResult() {
        return responseMessage.optionalExceptionResult();
    }

    @Override
    public String getIdentifier() {
        return responseMessage.getIdentifier();
    }

    @Override
    public MetaData getMetaData() {
        return responseMessage.getMetaData();
    }

    @Override
    public R getPayload() {
        if (isExceptional()) {
            throw new IllegalPayloadAccessException(
                    "This result completed exceptionally, payload is not available. "
                            + "Try calling 'exceptionResult' to see the cause of failure.",
                    optionalExceptionResult().orElse(null)
            );
        }
        return expectedResponseType.convert(responseMessage.getPayload());
    }

    @Override
    public Class<R> getPayloadType() {
        return expectedResponseType.responseMessagePayloadType();
    }

    @Override
    public QueryResponseMessage<R> withMetaData(Map<String, ?> metaData) {
        return new ConvertingResponseMessage<>(expectedResponseType, responseMessage.withMetaData(metaData));
    }

    @Override
    public QueryResponseMessage<R> andMetaData(Map<String, ?> additionalMetaData) {
        return new ConvertingResponseMessage<>(expectedResponseType, responseMessage.andMetaData(additionalMetaData));
    }
}
