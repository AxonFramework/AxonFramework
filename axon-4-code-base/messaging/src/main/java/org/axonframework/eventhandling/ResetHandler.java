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

package org.axonframework.eventhandling;

import org.axonframework.eventhandling.replay.ResetContext;
import org.axonframework.messaging.annotation.MessageHandler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that can be placed on a method that is to be invoked when a reset is being prepared. Handlers may throw an
 * exception to veto against a reset.
 *
 * @author Allard Buijze
 * @since 3.2
 */
@Retention(RetentionPolicy.RUNTIME)
@MessageHandler(messageType = ResetContext.class)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface ResetHandler {

}
