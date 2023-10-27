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

package org.axonframework.spring.messaging;

import org.axonframework.eventhandling.EventMessage;
import org.springframework.messaging.Message;

/**
 * Interface describing a mechanism that converts Spring Messages from an Axon Event Messages and vice versa.
 *
 * @author Reda.Housni-Alaoui
 * @since 3.1
 */
public interface EventMessageConverter {

	/**
	 * Converts Axon {@code event} into Spring message.
	 *
	 * @param event The Axon event to convert
	 * @param <T>   The event payload type
	 * @return The outbound Spring message
	 */
	<T> Message<T> convertToOutboundMessage(EventMessage<T> event);

	/**
	 * Converts a Spring inbound {@code message} into an Axon event Message
	 *
	 * @param message The Spring message to convert
	 * @param <T>     The message payload type
	 * @return The inbound Axon event message
	 */
	<T> EventMessage<T> convertFromInboundMessage(Message<T> message);
}
