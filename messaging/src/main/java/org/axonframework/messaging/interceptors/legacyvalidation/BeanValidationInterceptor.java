/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.interceptors.legacyvalidation;

import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import javax.annotation.Nonnull;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Interceptor that applies JSR303 bean validation on incoming {@link Message}s. When validation on a message fails, a
 * {@link JSR303ViolationException} is thrown, holding the constraint violations. This interceptor can either be used as
 * a {@link MessageHandlerInterceptor} or as a {@link MessageDispatchInterceptor}.
 *
 * @author Allard Buijze
 * @since 1.1
 * @deprecated in favor of using {@link org.axonframework.messaging.interceptors.BeanValidationInterceptor} which moved
 * to jakarta.
 */
@Deprecated
public class BeanValidationInterceptor<T extends Message<?>>
        implements MessageHandlerInterceptor<T>, MessageDispatchInterceptor<T> {

    private final ValidatorFactory validatorFactory;

    /**
     * Initializes a validation interceptor using a default {@link ValidatorFactory}.
     *
     * @see Validation#buildDefaultValidatorFactory()
     */
    public BeanValidationInterceptor() {
        this(Validation.buildDefaultValidatorFactory());
    }

    /**
     * Initializes a validation interceptor using the given {@link ValidatorFactory}.
     *
     * @param validatorFactory the factory providing {@link Validator} instances for this interceptor
     */
    public BeanValidationInterceptor(ValidatorFactory validatorFactory) {
        this.validatorFactory = validatorFactory;
    }

    @Override
    public Object handle(@Nonnull UnitOfWork<? extends T> unitOfWork, @Nonnull InterceptorChain interceptorChain)
            throws Exception {
        handle(unitOfWork.getMessage());
        return interceptorChain.proceed();
    }

    @Override
    @Nonnull
    public BiFunction<Integer, T, T> handle(@Nonnull List<? extends T> messages) {
        return (index, message) -> {
            Validator validator = validatorFactory.getValidator();
            Set<ConstraintViolation<Object>> violations = validateMessage(message.getPayload(), validator);
            if (violations != null && !violations.isEmpty()) {
                throw new JSR303ViolationException(violations);
            }
            return message;
        };
    }

    /**
     * Validate the given {@code message} using the given {@code validator}. The default implementation merely calls
     * {@code validator.validate(message)}.
     * <p>
     * Subclasses may override this method to alter the validation behavior in specific cases. Although the {@code null}
     * is accepted as return value to indicate that there are no constraint violations, implementations are encouraged
     * to return an empty Set instead.
     *
     * @param message   the message to validate
     * @param validator the validator provided by the validator factory
     * @return a set of {@link ConstraintViolation}s. May also return {@code null}
     */
    protected Set<ConstraintViolation<Object>> validateMessage(Object message, Validator validator) {
        return validator.validate(message);
    }
}
