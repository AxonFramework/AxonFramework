package org.axonframework.queryhandling;

import org.axonframework.common.Registration;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * @author Marc Gathier
 * @since 3.1
 */
public class AnnotationQueryHandlerAdapter implements QueryHandlerAdapter {
    private final Object target;
    private final ParameterResolverFactory parameterResolverFactory;

    public AnnotationQueryHandlerAdapter(Object target, ParameterResolverFactory parameterResolverFactory) {
        this.target = target;
        this.parameterResolverFactory = parameterResolverFactory;
    }

    @Override
    public Registration subscribe(QueryBus queryBus) {
        Arrays.stream(target.getClass().getMethods()).forEach(m -> {
            if( m.isAnnotationPresent(QueryHandler.class)) {
                QueryHandler qh = m.getAnnotation(QueryHandler.class);
                String queryName = qh.queryName().isEmpty() ? m.getParameters()[0].getType().getName() : qh.queryName();
                String responseName = qh.responseName().isEmpty() ? m.getReturnType().getName() : qh.responseName();
                ParameterResolver[] parameterResolvers = new ParameterResolver[m.getParameterCount()];
                for( int i = 0 ; i < m.getParameterCount(); i++) {
                    parameterResolvers[i] = parameterResolverFactory.createInstance(m, m.getParameters(), i);
                }

                queryBus.subscribe(queryName, responseName, (qm) -> runQuery(m, parameterResolvers, target, qm));
            }
        });
        return () -> true;
    }

    private Object runQuery(Method method, ParameterResolver[] parameterResolvers, Object target, QueryMessage<?> queryMessage) {
        try {
            Object[] params = new Object[method.getParameterCount()];
            for( int i = 0 ; i < method.getParameterCount(); i++) {
                params[i] = parameterResolvers[i].resolveParameterValue(queryMessage);
            }

            return method.invoke(target, params);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

}
