package org.axonframework.commandhandling.annotation;

import org.axonframework.common.annotation.ParameterResolver;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.domain.Message;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;

import java.lang.annotation.Annotation;

/**
 * ParameterResolverFactory that add support for the UnitOfWork parameter type in annotated handlers.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class CurrentUnitOfWorkParameterResolverFactory extends ParameterResolverFactory implements ParameterResolver {

    @Override
    public ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType,
                                            Annotation[] parameterAnnotations) {
        if (UnitOfWork.class.isAssignableFrom(parameterType)) {
            return this;
        }
        return null;
    }

    @Override
    public Object resolveParameterValue(Message message) {
        return CurrentUnitOfWork.get();
    }

    @Override
    public boolean matches(Message message) {
        return CommandMessage.class.isInstance(message) && CurrentUnitOfWork.isStarted();
    }
}
