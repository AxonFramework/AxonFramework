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

package org.axonframework.examples.addressbook.web.listener;

import org.axonframework.examples.addressbook.web.dto.AddressDTO;
import org.axonframework.examples.addressbook.web.dto.ContactDTO;
import org.axonframework.examples.addressbook.web.dto.RemovedDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.flex.messaging.MessageTemplate;
import org.springframework.stereotype.Component;

/**
 * @author Jettro Coenradie
 */
@Component
public class UpdateMessageProducerForFlex {

    private MessageTemplate template;

    @Autowired
    public UpdateMessageProducerForFlex(MessageTemplate template) {
        this.template = template;
    }

    public void sendContactUpdate(final ContactDTO contactDTO) {
        template.send(contactDTO);
    }

    public void sendAddressUpdate(final AddressDTO addressDTO) {
        template.send(addressDTO);
    }

    public void sendRemovedUpdate(final RemovedDTO removedDTO) {
        template.send(removedDTO);
    }
}
