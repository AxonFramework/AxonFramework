/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.eventhandling.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that marks a method to be called before a transactional batch of events is handled. This gives the
 * annotated method the opportunity to prepare resources and start necessary transactions.
 * <p/>
 * Methods annotated by this annotation may have zero or one parameter of type {@link
 * org.axonframework.eventhandling.TransactionStatus}. The transaction status object contains information about the
 * transactional batch. It also provides an option to configure transaction parameters such as batch size.
 * <p/>
 * For each time the annotated method is called, the method annotated with {@link AfterTransaction} will be called too.
 * <p/>
 * For each class hierarchy, only a single method annotated with <code>@BeforeTransaction</code> will be invoked. This
 * is always a method on the most specific class (i.e. subclass) in the hierarchy. If that class contains several
 * annotated methods, the behavior is undefined.
 *
 * @author Allard Buijze
 * @see org.axonframework.eventhandling.TransactionStatus#setMaxTransactionSize(int)
 * @since 0.3
 * @deprecated Transaction management on the EventListener level is deprecated. Use a transaction aware Cluster
 *             instead.
 */
@Documented
@Deprecated
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BeforeTransaction {

}
