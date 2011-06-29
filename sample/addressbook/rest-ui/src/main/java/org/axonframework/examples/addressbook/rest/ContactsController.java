package org.axonframework.examples.addressbook.rest;

/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.sample.app.api.*;
import org.axonframework.sample.app.command.ContactNameRepository;
import org.axonframework.sample.app.query.AddressEntry;
import org.axonframework.sample.app.query.ContactEntry;
import org.axonframework.sample.app.query.ContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * <p>Controller that facilitates the rest based interface for contacts. You can use the following methods:</p>
 * <ul>
 * <li>/contacts : GET, returns the list of all contacts</li>
 * <li>/contacts : POST, creates a new contact</li>
 * <li>/contacts : PUT, changes an existing contact</li>
 * <li>/contacts : DELETE, removes a contact</li>
 * <li>/contacts/{identifier} : GET, returns the details of a contact</li>
 * <li>/contacts/{identifier}/address : PUT, creates a new address or updates an existing one based on address type</li>
 * <li>/contacts/{identifier}/address : DELETE, removes the address of provided type</li>
 * </ul>
 * <p>These are all the contacts related methods that this rest interface currently supports. We expect all provided
 * data to be in json format and all data is returned in json format as well. Exceptions are returned as html error
 * pages.</p>
 *
 * @author Jettro Coenradie
 */
@Controller
@RequestMapping(value = "/contacts")
public class ContactsController {
    private final static Logger logger = LoggerFactory.getLogger(ContactsController.class);

    @Autowired
    private ContactRepository repository;

    @Autowired
    private ContactNameRepository contactNameRepository;

    @Autowired
    private CommandBus commandBus;

    /**
     * Returns a list of all contacts
     *
     * @return List containing the contacts
     */
    @RequestMapping(method = RequestMethod.GET)
    public
    @ResponseBody
    List<ContactEntry> list() {
        return repository.findAllContacts();
    }

    /**
     * Returns the details for a specific contact
     *
     * @param identifier String containing the identifier of the contact to obtains the details for
     * @return Map containing the available data (name, identifier and addresses)
     */
    @RequestMapping(value = "{identifier}", method = RequestMethod.GET)
    public
    @ResponseBody
    Map<String, Object> details(@PathVariable String identifier) {
        List<AddressEntry> addressesForContact = repository.findAllAddressesForContact(identifier);
        String name;
        if (addressesForContact.size() > 0) {
            name = addressesForContact.get(0).getName();
        } else {
            name = repository.loadContactDetails(identifier).getName();
        }
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("addresses", addressesForContact);
        map.put("identifier", identifier);
        map.put("name", name);
        return map;
    }

    /**
     * Submit a new contact.
     *
     * @param contact ContactEntry object that contains the entered data
     * @throws ContactNameAlreadyTakenException
     *          If the contact name has already been taken
     */
    @RequestMapping(method = RequestMethod.POST)
    public void create(@RequestBody @Valid ContactEntry contact) throws ContactNameAlreadyTakenException {
        contactHasErrors(contact);

        CreateContactCommand command = new CreateContactCommand();
        command.setNewContactName(contact.getName());

        commandBus.dispatch(command);
    }

    /**
     * Change the name of a contact
     *
     * @param contact ContactEntry object containing the identifier and the new name of the contact
     * @throws ContactNameAlreadyTakenException
     *          if the new contact name is already taken
     */
    @RequestMapping(method = RequestMethod.PUT)
    public void update(@RequestBody @Valid ContactEntry contact) throws ContactNameAlreadyTakenException {
        contactHasErrors(contact);

        ChangeContactNameCommand command = new ChangeContactNameCommand();
        command.setContactNewName(contact.getName());
        command.setContactId(contact.getIdentifier());

        commandBus.dispatch(command);
    }

    /**
     * Removed the provided contact
     *
     * @param contact ContactEntry containing the required details to remove a contact
     * @throws MissingServletRequestParameterException
     *          if the required parameter identifier is nto available
     */
    @RequestMapping(method = RequestMethod.DELETE)
    public void delete(@RequestBody ContactEntry contact) throws MissingServletRequestParameterException {
        if (!StringUtils.hasText(contact.getIdentifier())) {
            throw new MissingServletRequestParameterException("identifier", "String");
        }
        RemoveContactCommand command = new RemoveContactCommand();
        command.setContactId(contact.getIdentifier());
        commandBus.dispatch(command);
    }

    /**
     * If the provided type for the provided contact is already available, we do an update. If the type of address
     * is not yet available we create a new address.
     *
     * @param address Address to create or update
     */
    @RequestMapping(value = "{identifier}/address", method = RequestMethod.PUT)
    public void createOrUpdateAddress(@RequestBody @Valid AddressEntry address) {
        RegisterAddressCommand command = new RegisterAddressCommand();
        command.setAddressType(address.getAddressType());
        command.setCity(address.getCity());
        command.setContactId(address.getIdentifier());
        command.setStreetAndNumber(address.getStreetAndNumber());
        command.setZipCode(address.getZipCode());
        commandBus.dispatch(command);
    }

    /**
     * Removes the address of the provided type from the contact with the provided id.
     *
     * @param address Address to remove
     * @throws MissingServletRequestParameterException
     *          if the type or identifier are not provided
     */
    @RequestMapping(value = "{identifier}/address", method = RequestMethod.DELETE)
    public void deleteAddress(@RequestBody AddressEntry address) throws MissingServletRequestParameterException {
        if (!StringUtils.hasText(address.getIdentifier())) {
            throw new MissingServletRequestParameterException("identifier", "String");
        }
        if (null == address.getAddressType()) {
            throw new MissingServletRequestParameterException("addressType", "PRIVATE,WORK,VACATION");
        }
        RemoveAddressCommand command = new RemoveAddressCommand();
        command.setContactId(address.getIdentifier());
        command.setAddressType(address.getAddressType());
        commandBus.dispatch(command);

    }


    /**
     * Checks if the entered data for a contact is valid and if the provided contact has not yet been taken.
     *
     * @param contact Contact to validate
     * @throws ContactNameAlreadyTakenException
     *          if the passed contact name is already taken
     */
    private void contactHasErrors(ContactEntry contact) throws ContactNameAlreadyTakenException {
        if (!contactNameRepository.vacantContactName(contact.getName())) {
            throw new ContactNameAlreadyTakenException(contact.getName());
        }
    }
}
