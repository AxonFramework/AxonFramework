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

package org.axonframework.examples.addressbook.vaadin.ui;

import com.vaadin.data.Item;
import com.vaadin.data.util.BeanItem;
import com.vaadin.terminal.ThemeResource;
import com.vaadin.ui.*;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.examples.addressbook.vaadin.data.ContactFormBean;
import org.axonframework.sample.app.api.AbstractOrderCommand;
import org.axonframework.sample.app.api.ChangeContactNameCommand;
import org.axonframework.sample.app.api.CreateContactCommand;
import org.axonframework.sample.app.api.RemoveContactCommand;

import java.io.Serializable;

/**
 * <p>Form that can be used to create new contacts, change the details of an existing contact and to remove a contact.
 * </p>
 * <p>The form makes use of the command bus to send commands to the backend</p>
 *
 * @author Jettro Coenradie
 */
public class ContactForm extends Form implements Button.ClickListener {
    private Button save = new Button("Save", (Button.ClickListener) this);
    private Button cancel = new Button("Cancel", (Button.ClickListener) this);
    private Button edit = new Button("Edit", (Button.ClickListener) this);
    private Button delete = new Button("Delete", (Button.ClickListener) this);

    private boolean newContactMode = false;
    private CommandBus commandBus;

    public ContactForm(CommandBus commandBus) {
        this.commandBus = commandBus;
        save.setIcon(new ThemeResource(Theme.save));
        cancel.setIcon(new ThemeResource(Theme.cancel));
        edit.setIcon(new ThemeResource(Theme.documentEdit));
        delete.setIcon(new ThemeResource(Theme.documentDelete));

        createAndSetFooter();
    }

    @Override
    public void buttonClick(Button.ClickEvent event) {
        Button source = event.getButton();
        if (source == save) {
            handleSave();
        } else if (source == cancel) {
            handleCancel();
        } else if (source == delete) {
            handleDelete();
        } else if (source == edit) {
            setReadOnly(false);
        }
    }

    @Override
    public void setItemDataSource(Item newDataSource) {
        newContactMode = false;
        if (newDataSource != null) {
            super.setItemDataSource(newDataSource);
            setReadOnly(true);
            getFooter().setVisible(true);
        } else {
            super.setItemDataSource(null);
            getFooter().setVisible(false);
        }
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        super.setReadOnly(readOnly);
        save.setVisible(!readOnly);
        cancel.setVisible(!readOnly);
        edit.setVisible(readOnly);
        delete.setVisible(readOnly);
    }

    /**
     * Setup the form to create a new Contact
     */
    public void addContact() {
        setItemDataSource(new BeanItem<ContactFormBean>(new ContactFormBean()));
        newContactMode = true;
        setReadOnly(false);
    }

    private void handleDelete() {
        setReadOnly(true);
        ContactFormBean contact = obtainContactFormBeanFromDatasource();
        RemoveContactCommand command = new RemoveContactCommand();
        command.setContactId(contact.getIdentifier());
        commandBus.dispatch(command);
        String message = "Removed the contact with name " + contact.getName();
        fireEvent(new FormIsSuccessfullyCommittedEvent(this));
        getApplication().getMainWindow().showNotification(message, Window.Notification.TYPE_TRAY_NOTIFICATION);
    }

    private void handleCancel() {
        if (newContactMode) {
            newContactMode = false;
            setItemDataSource(null);
        } else {
            discard();
        }
        setReadOnly(true);
    }

    private void handleSave() {
        String message;
        if (!isValid()) {
            return;
        }

        AbstractOrderCommand command;
        ContactFormBean contact = obtainContactFormBeanFromDatasource();

        if (newContactMode) {
            newContactMode = false;
            CreateContactCommand createCommand = new CreateContactCommand();
            createCommand.setNewContactName(contact.getName());
            command = createCommand;
            message = "Created new contact with name " + contact.getName();
        } else {
            ChangeContactNameCommand changeCommand = new ChangeContactNameCommand();
            changeCommand.setContactNewName(contact.getName());
            changeCommand.setContactId(contact.getIdentifier());
            command = changeCommand;
            message = "Changed name of contact into " + contact.getName();
        }
        commandBus.dispatch(command);
        fireEvent(new FormIsSuccessfullyCommittedEvent(this));
        setReadOnly(true);
        getApplication().getMainWindow().showNotification(message, Window.Notification.TYPE_TRAY_NOTIFICATION);
    }

    private ContactFormBean obtainContactFormBeanFromDatasource() {
        //noinspection unchecked
        return ((BeanItem<ContactFormBean>) getItemDataSource()).getBean();
    }


    private void createAndSetFooter() {
        HorizontalLayout footer = new HorizontalLayout();
        footer.setSpacing(true);
        footer.addComponent(save);
        footer.addComponent(cancel);
        footer.addComponent(edit);
        footer.addComponent(delete);
        footer.setVisible(false);
        setFooter(footer);
    }


    /*
        EVENTS
     */
    public class FormIsSuccessfullyCommittedEvent extends Component.Event {
        private String name;
        private String identifier;

        /**
         * Constructs a new event with the specified source component.
         *
         * @param source the source component of the event
         */
        public FormIsSuccessfullyCommittedEvent(Component source) {
            super(source);
            ContactFormBean contactFormBean = obtainContactFormBeanFromDatasource();
            name = contactFormBean.getName();
            identifier = contactFormBean.getIdentifier();
        }

        public String getIdentifier() {
            return identifier;
        }

        public String getName() {
            return name;
        }
    }

    public interface CommitListener extends Serializable {
        public void formIsCommitted(FormIsSuccessfullyCommittedEvent event);
    }

    public void addListener(CommitListener listener) {
        addListener(FormIsSuccessfullyCommittedEvent.class, listener, "formIsCommitted");
    }

    public void removeListener(CommitListener listener) {
        removeListener(FormIsSuccessfullyCommittedEvent.class, listener, "formIsCommitted");
    }
}
