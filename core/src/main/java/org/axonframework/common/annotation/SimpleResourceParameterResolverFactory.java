package org.axonframework.common.annotation;

import org.axonframework.domain.Message;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Implementation of the ParameterResolverFactory that injects a static list of resources into annotated handler
 * parameters. Any resource is eligible for injection when its type matches the parameter of the annotated handler
 * method.
 * <p/>
 * Since this factory is configured at runtime, it must be registered <em>before</em> any annotated handlers are
 * registered. Handlers that have been registered before will not have their parameter eligible for injection by this
 * factory.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SimpleResourceParameterResolverFactory extends ParameterResolverFactory {

    private final List<?> injectableResources;

    /**
     * Registers the given <code>injectableResources</code> for the ParameterResolverFactory. This method must be
     * called <em>before</em> any annotated handlers are registered. Handlers that have been registered before will not
     * have their parameter eligible for injection by this factory.
     *
     * @param injectableResources The resources eligible for injection. Each resource is evaluated in the order
     *                            provided by the iterator of the given collection.
     */
    public static void register(Collection<?> injectableResources) {
        ParameterResolverFactory.registerFactory(new SimpleResourceParameterResolverFactory(injectableResources));
    }

    private SimpleResourceParameterResolverFactory(Collection<?> injectableResources) {
        this.injectableResources = new ArrayList<Object>(injectableResources);
    }

    @Override
    public boolean supportsPayloadResolution() {
        return false;
    }

    @Override
    protected ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType,
                                               Annotation[] parameterAnnotations) {
        for (Object resource : injectableResources) {
            if (parameterType.isInstance(resource)) {
                return new SimpleResourceParameterResolver(resource);
            }
        }
        return null;
    }

    private static class SimpleResourceParameterResolver implements ParameterResolver {

        private final Object resource;

        public SimpleResourceParameterResolver(Object resource) {
            this.resource = resource;
        }

        @Override
        public Object resolveParameterValue(Message message) {
            return resource;
        }

        @Override
        public boolean matches(Message message) {
            return true;
        }
    }
}
