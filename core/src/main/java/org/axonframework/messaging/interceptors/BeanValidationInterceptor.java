/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.interceptors;

import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Interceptor that applies JSR303 bean validation on incoming messages. When validation on a message fails, a
 * JSR303ViolationException is thrown, holding the constraint violations.
 * <p/>
 * This interceptor can either be used as a {@link MessageHandlerInterceptor} or as a {@link
 * MessageDispatchInterceptor}.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class BeanValidationInterceptor<T extends Message<?>> implements MessageHandlerInterceptor<T>,
                                                                        MessageDispatchInterceptor<T> {

    private final ValidatorFactory validatorFactory;

    /**
     * Initializes a validation interceptor using a default ValidatorFactory (see {@link
     * javax.validation.Validation#buildDefaultValidatorFactory()}).
     */
    public BeanValidationInterceptor() {
        this(Validation.buildDefaultValidatorFactory());
    }

    /**
     * Initializes a validation interceptor using the given ValidatorFactory.
     *
     * @param validatorFactory the factory providing Validator instances for this interceptor.
     */
    public BeanValidationInterceptor(ValidatorFactory validatorFactory) {
        this.validatorFactory = validatorFactory;
    }

    @Override
    public Object handle(UnitOfWork<? extends T> unitOfWork, InterceptorChain interceptorChain) throws Exception {
        handle(unitOfWork.getMessage());
        return interceptorChain.proceed();
    }

    @Override
    public Function<Integer, T> handle(List<T> messages) {
        return index -> {
            T message = messages.get(index);
            Validator validator = validatorFactory.getValidator();
            Set<ConstraintViolation<Object>> violations = validateMessage(message.getPayload(), validator);
            if (violations != null && !violations.isEmpty()) {
                throw new JSR303ViolationException("One or more JSR303 constraints were violated.", violations);
            }
            return message;
        };
    }

    /**
     * Validate the given <code>message</code> using the given <code>validator</code>. The default implementation
     * merely calls <code>validator.validate(message)</code>.
     * <p/>
     * Subclasses may override this method to alter the validation behavior in specific cases. Although the
     * <code>null</code> is accepted as return value to indicate that there are no constraint violations,
     * implementations are encouraged to return an empty Set instead.
     *
     * @param message   The message to validate
     * @param validator The validator provided by the validator factory
     * @return a set of Constraint Violations. May also return <code>null</code>.
     */
    protected Set<ConstraintViolation<Object>> validateMessage(Object message, Validator validator) {
        return validator.validate(message);
    }
}
