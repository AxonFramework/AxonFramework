package org.axonframework.serialization;

import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;

import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;

import java.beans.ConstructorProperties;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumSet;

/**
 * Enumeration of serializers for testing purposes.
 * 
 * @author JohT
 */
public enum TestSerializer {

    JAVA {
        @SuppressWarnings("deprecation")
        private Serializer serializer = JavaSerializer.builder().build();
        
        @Override
        public Serializer getSerializer() {
            return serializer;
        }
        
        @Override
        protected  String serialize(Object object) {
            return Base64.getEncoder().encodeToString(getSerializer().serialize(object, byte[].class).getData());
        }

        @Override
        protected <T> T deserialize(String serialized, Class<T> type) {
            return getSerializer().deserialize(asSerializedData(Base64.getDecoder().decode(serialized), type));
        }
       
    },
    XTREAM {
        private Serializer serializer = createSerializer();

        private XStreamSerializer createSerializer() {
            XStreamSerializer xStreamSerializer = XStreamSerializer.builder().build();
            xStreamSerializer.getXStream().setClassLoader(this.getClass().getClassLoader());
            return xStreamSerializer;
        }

        @Override
        public Serializer getSerializer() {
            return serializer;
        }
    },
    JACKSON {
        private Serializer serializer = JacksonSerializer.builder().build();

        @Override
        public Serializer getSerializer() {
            return serializer;
        }
        
    },
    JACKSON_ONLY_ACCEPT_CONSTRUCTOR_PARAMETERS {
        private Serializer serializer = JacksonSerializer.builder().objectMapper(OnlyAcceptConstructorPropertiesAnnotation.attachTo(new ObjectMapper())).build();

        @Override
        public Serializer getSerializer() {
            return serializer;
        }
    },

    ;

    protected  String serialize(Object object) {
        return new String(getSerializer().serialize(object, byte[].class).getData());
    }

    protected <T> T deserialize(String serialized, Class<T> type) {
        return getSerializer().deserialize(asSerializedData(serialized.getBytes(), type));
    }
    
    public abstract Serializer getSerializer();

    @SuppressWarnings("unchecked")
    public <T> T serializeDeserialize(T object) {
        return deserialize(serialize(object), (Class<T>) object.getClass());
    }

    public static final Collection<TestSerializer> all() {
        return EnumSet.allOf(TestSerializer.class);
    }

    static <T> SerializedObject<byte[]> asSerializedData(byte[] serialized, Class<T> type) {
        SimpleSerializedType serializedType = new SimpleSerializedType(type.getName(), null);
        return new SimpleSerializedObject<>(serialized, byte[].class, serializedType);
    }
    
    private static class OnlyAcceptConstructorPropertiesAnnotation extends JacksonAnnotationIntrospector {

        private static final long serialVersionUID = 1L;

        public static final ObjectMapper attachTo(ObjectMapper objectMapper) {
            return objectMapper.setAnnotationIntrospector(new OnlyAcceptConstructorPropertiesAnnotation());
        }

        @Override
        public Mode findCreatorAnnotation(MapperConfig<?> config, Annotated annotated) {
            return (annotated.hasAnnotation(ConstructorProperties.class)) ? super.findCreatorAnnotation(config, annotated) : Mode.DISABLED;
        }
    }
}