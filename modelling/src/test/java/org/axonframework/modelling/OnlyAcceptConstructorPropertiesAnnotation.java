package org.axonframework.modelling;

import java.beans.ConstructorProperties;

import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;

/**
 * Internal {@link AnnotationIntrospector} for jackson to test JSON serialization ignoring all
 * creator annotations except for the {@link ConstructorProperties}-annotation.
 * 
 * @author JohT
 */
public class OnlyAcceptConstructorPropertiesAnnotation extends JacksonAnnotationIntrospector {

    private static final long serialVersionUID = 1L;

    public static final ObjectMapper attachTo(ObjectMapper objectMapper) {
        return objectMapper.setAnnotationIntrospector(new OnlyAcceptConstructorPropertiesAnnotation());
    }
    
    @Override
    public Mode findCreatorAnnotation(MapperConfig<?> config, Annotated annotated) {
        return (annotated.hasAnnotation(ConstructorProperties.class))? super.findCreatorAnnotation(config, annotated) : Mode.DISABLED;
    }
}