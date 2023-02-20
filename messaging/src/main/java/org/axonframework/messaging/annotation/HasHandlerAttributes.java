/*
 * Copyright (c) 2010-2023. Axon Framework
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.messaging.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation indicating the meta-annotated annotation is used for {@link org.axonframework.messaging.annotation.MessageHandlingMember}s.
 * As such it carries specific attributes which are important to the {@code MessageHandlingMember} in an annotation
 * driven Axon application.
 * <p>
 * These attributes can be extracted into an {@link AnnotatedHandlerAttributes} objects through its constructor.
 *
 * @author Steven van Beelen
 * @since 4.5
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface HasHandlerAttributes {

}
