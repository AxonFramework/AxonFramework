package org.axonframework.serializer.gae;

import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.core.util.ClassLoaderReference;
import com.thoughtworks.xstream.core.util.CompositeClassLoader;
import com.thoughtworks.xstream.io.xml.XppDriver;
import org.springframework.beans.factory.FactoryBean;

/**
 * @author Jettro Coenradie
 */
public class XStreamFactory implements FactoryBean {
    @Override
    public Object getObject() throws Exception {
        return new GaeXStream(
                new PureJavaReflectionProvider(),
                new XppDriver(),
                new ClassLoaderReference(new CompositeClassLoader()));
    }

    @Override
    public Class<?> getObjectType() {
        return GaeXStream.class;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }
}
