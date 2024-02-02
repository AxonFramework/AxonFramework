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

package org.axonframework.messaging.interceptors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Interceptor that applies JSR303 bean validation on incoming {@link Message}s. When validation on a message fails, a
 * {@link JSR303ViolationException} is thrown, holding the constraint violations. This interceptor can either be used as
 * a {@link MessageHandlerInterceptor} or as a {@link MessageDispatchInterceptor}.
 *
 * @author Allard Buijze
 * @since 1.1
 */
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
    public <M extends T, R> MessageStream<? extends R> interceptOnDispatch(@Nonnull M message,
                                                                           @Nullable ProcessingContext context,
                                                                           @Nonnull InterceptorChain<M, R> interceptorChain) {
        return intercept(message, context, interceptorChain);
    }

    @Override
    public <M extends T, R> MessageStream<? extends R> interceptOnHandle(@Nonnull M message,
                                                                         @Nonnull ProcessingContext context,
                                                                         @Nonnull InterceptorChain<M, R> interceptorChain) {
        return intercept(message, context, interceptorChain);
    }

    private <M extends T, R> MessageStream<? extends R> intercept(M message, @Nullable ProcessingContext context,
                                                                  InterceptorChain<M, R> interceptorChain) {
        Set<ConstraintViolation<Object>> violations = validate(message);
        if (!violations.isEmpty()) {
            return MessageStream.fromFuture(CompletableFuture.failedFuture(new JSR303ViolationException(violations)));
        }
        return interceptorChain.proceed(message, context);
    }

    private Set<ConstraintViolation<Object>> validate(Message<?> message) {
        Validator validator = validatorFactory.getValidator();
        return validateMessage(message.getPayload(), validator);
    }

    @Deprecated
    @Override
    public Object handle(@Nonnull UnitOfWork<? extends T> unitOfWork, @Nonnull InterceptorChain interceptorChain)
            throws Exception {
        handle(unitOfWork.getMessage());
        return interceptorChain.proceedSync();
    }

    @Deprecated
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
