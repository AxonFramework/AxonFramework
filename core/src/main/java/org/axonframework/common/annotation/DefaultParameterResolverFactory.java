package org.axonframework.common.annotation;

import org.axonframework.common.CollectionUtils;
import org.axonframework.domain.Message;

import java.lang.annotation.Annotation;

import static org.axonframework.common.CollectionUtils.getAnnotation;

/**
 * Factory for the default parameter resolvers. This factory is capable for providing parameter resolvers for Message,
 * MetaData and @MetaData annotated parameters.
 *
 * @author Allard Buijze
 * @since 2.0
 */
class DefaultParameterResolverFactory extends ParameterResolverFactory {

    @Override
    public ParameterResolver createInstance(Annotation[] methodAnnotations, Class<?> parameterType,
                                            Annotation[] parameterAnnotations) {
        if (Message.class.isAssignableFrom(parameterType)) {
            return new MessageParameterResolver(parameterType);
        }
        if (getAnnotation(parameterAnnotations, MetaData.class) != null) {
            return new AnnotatedMetaDataParameterResolver(CollectionUtils.getAnnotation(parameterAnnotations,
                                                                                        MetaData.class), parameterType);
        }
        if (org.axonframework.domain.MetaData.class.isAssignableFrom(parameterType)) {
            return MetaDataParameterResolver.INSTANCE;
        }
        return null;
    }

    /**
     * Creates a new payload resolver, which passes a message's payload as parameter.
     *
     * @param parameterType The type of payload supported by this resolver
     * @return a payload resolver that returns the payload of a message when of the given <code>parameterType</code>
     */
    public ParameterResolver newPayloadResolver(Class<?> parameterType) {
        return new PayloadParameterResolver(parameterType);
    }

    private static class AnnotatedMetaDataParameterResolver implements ParameterResolver {

        private final MetaData metaData;
        private final Class parameterType;

        public AnnotatedMetaDataParameterResolver(MetaData metaData, Class parameterType) {
            this.metaData = metaData;
            this.parameterType = parameterType;
        }

        @Override
        public Object resolveParameterValue(Message message) {
            return message.getMetaData().get(metaData.key());
        }

        @Override
        public boolean matches(Message message) {
            return !(parameterType.isPrimitive() || metaData.required())
                    || (
                    message.getMetaData().containsKey(metaData.key())
                            && parameterType.isInstance(message.getMetaData().get(metaData.key()))
            );
        }
    }

    private static final class MetaDataParameterResolver implements ParameterResolver {

        private static final MetaDataParameterResolver INSTANCE = new MetaDataParameterResolver();

        private MetaDataParameterResolver() {
        }

        @Override
        public Object resolveParameterValue(Message message) {
            return message.getMetaData();
        }

        @Override
        public boolean matches(Message message) {
            return true;
        }
    }

    private static class MessageParameterResolver implements ParameterResolver {

        private final Class<?> parameterType;

        public MessageParameterResolver(Class<?> parameterType) {
            this.parameterType = parameterType;
        }

        @Override
        public Object resolveParameterValue(Message message) {
            return message;
        }

        @Override
        public boolean matches(Message message) {
            return parameterType.isInstance(message);
        }
    }

    private static class PayloadParameterResolver implements ParameterResolver {
        private final Class<?> payloadType;

        public PayloadParameterResolver(Class<?> payloadType) {
            this.payloadType = payloadType;
        }

        @Override
        public Object resolveParameterValue(Message message) {
            return message.getPayload();
        }

        @Override
        public boolean matches(Message message) {
            return payloadType.isAssignableFrom(message.getPayloadType());
        }
    }
}
