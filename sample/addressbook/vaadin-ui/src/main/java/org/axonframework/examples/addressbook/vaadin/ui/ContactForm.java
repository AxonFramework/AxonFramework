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
import com.vaadin.ui.Button;
import com.vaadin.ui.Form;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Window;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.examples.addressbook.vaadin.data.ChangeContactNameBean;
import org.axonframework.examples.addressbook.vaadin.data.CreateContactBean;
import org.axonframework.sample.app.api.AbstractOrderCommand;
import org.axonframework.sample.app.api.ChangeContactNameCommand;
import org.axonframework.sample.app.api.CreateContactCommand;

/**
 * @author Jettro Coenradie
 */
public class ContactForm extends Form implements Button.ClickListener {
    private Button save = new Button("Save", (Button.ClickListener) this);
    private Button cancel = new Button("Cancel", (Button.ClickListener) this);
    private Button edit = new Button("Edit", (Button.ClickListener) this);

    private boolean newContactMode = false;
    private CommandBus commandBus;

    public ContactForm(CommandBus commandBus) {
        this.commandBus = commandBus;

        setWriteThrough(false);
        HorizontalLayout footer = new HorizontalLayout();
        footer.setSpacing(true);
        footer.addComponent(save);
        footer.addComponent(cancel);
        footer.addComponent(edit);
        footer.setVisible(false);

        setFooter(footer);

    }

    @Override
    public void buttonClick(Button.ClickEvent event) {
        Button source = event.getButton();
        if (source == save) {
            if (!isValid()) {
                return;
            }
            commit();

            AbstractOrderCommand command;
            String message;
            if (newContactMode) {
                newContactMode = false;
                // add contact to internal container
                BeanItem<CreateContactBean> contact = (BeanItem<CreateContactBean>) getItemDataSource();
                CreateContactCommand createCommand = new CreateContactCommand();
                createCommand.setNewContactName(contact.getBean().getNewName());
                command = createCommand;
                message = "Created new contact with name " + contact.getBean().getNewName();
            } else {
                BeanItem<ChangeContactNameBean> contact = (BeanItem<ChangeContactNameBean>) getItemDataSource();
                ChangeContactNameCommand changeCommand = new ChangeContactNameCommand();
                changeCommand.setContactNewName(contact.getBean().getChangedName());
                changeCommand.setContactId(contact.getBean().getIdentifier());
                command = changeCommand;
                message = "Changed name of contact into " + contact.getBean().getChangedName();
            }
            getApplication().getMainWindow().showNotification(message, Window.Notification.TYPE_TRAY_NOTIFICATION);
            commandBus.dispatch(command);
            setReadOnly(true);
        } else if (source == cancel) {
            if (newContactMode) {
                newContactMode = false;
                setItemDataSource(null);
            } else {
                discard();
            }
            setReadOnly(true);
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
    }


    public void addContact() {
        setItemDataSource(new BeanItem<CreateContactBean>(new CreateContactBean()));
        newContactMode = true;
        setReadOnly(false);
    }
}
