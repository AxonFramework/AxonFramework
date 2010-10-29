package org.axonframework.examples.addressbook.web;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.callbacks.NoOpCallback;
import org.axonframework.domain.StringAggregateIdentifier;
import org.axonframework.sample.app.api.ChangeContactNameCommand;
import org.axonframework.sample.app.api.CreateContactCommand;
import org.axonframework.sample.app.query.AddressEntry;
import org.axonframework.sample.app.query.ContactEntry;
import org.axonframework.sample.app.query.ContactRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.validation.Valid;
import java.util.List;

/**
 * @author Jettro Coenradie
 */
@Controller
@RequestMapping(value = "/contacts")
public class ContactsController {
    @Autowired
    private ContactRepository repository;

    @Autowired
    private CommandBus commandBus;

    @RequestMapping(method = RequestMethod.GET)
    public String list(Model model) {
        model.addAttribute("contacts", repository.findAllContacts());
        return "contacts/list";
    }

    @RequestMapping(value = "{identifier}", method = RequestMethod.GET)
    public String details(@PathVariable String identifier, Model model) {
        List<AddressEntry> addressesForContact = repository.findAllAddressesForContact(identifier);
        String name = "";
        if (addressesForContact.size() > 0) {
            name = addressesForContact.get(0).getName();
        } else {
            name = repository.loadContactDetails(identifier).getName();
        }
        model.addAttribute("addresses", addressesForContact);
        model.addAttribute("identifier", identifier);
        model.addAttribute("name", name);
        return "contacts/details";
    }

    @RequestMapping(value = "{identifier}/edit", method = RequestMethod.GET)
    public String formEdit(@PathVariable String identifier, Model model) {
        ContactEntry contactEntry = repository.loadContactDetails(identifier);
        model.addAttribute("contact",contactEntry);
        return "contacts/edit";
    }

    @RequestMapping(value = "{identifier}/edit", method = RequestMethod.POST)
    public String formEditSubmit(@ModelAttribute("contact") ContactEntry contact, BindingResult bindingResult) {
        if (!bindingResult.hasErrors()) {
            ChangeContactNameCommand command = new ChangeContactNameCommand();
            command.setContactNewName(contact.getName());
            command.setContactId(contact.getIdentifier());

            commandBus.dispatch(command);

            return "redirect:/contacts";
        }
        return "contacts/" + contact.getIdentifier() + "/edit";

    }

    @RequestMapping(value = "new", method = RequestMethod.GET)
    public String formNew(Model model) {
        model.addAttribute("contact",new ContactEntry());
        return "contacts/edit";
    }

    @RequestMapping(value = "new", method = RequestMethod.POST)
    public String formNewSubmit(@ModelAttribute("contact") ContactEntry contact, BindingResult bindingResult) {
        if (!bindingResult.hasErrors()) {
            CreateContactCommand command = new CreateContactCommand();
            command.setNewContactName(contact.getName());

            commandBus.dispatch(command);

            return "redirect:/contacts";
        }
        return "contacts/" + contact.getIdentifier() + "/new";

    }

}
