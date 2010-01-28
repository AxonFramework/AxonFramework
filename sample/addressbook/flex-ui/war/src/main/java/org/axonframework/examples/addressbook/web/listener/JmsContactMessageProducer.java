package org.axonframework.examples.addressbook.web.listener;

import org.axonframework.examples.addressbook.web.dto.AddressDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

/**
 * @author Jettro Coenradie
 */
public class JmsContactMessageProducer {
    private JmsTemplate jmsTemplate;
    private Destination destination;

    @Autowired
    public JmsContactMessageProducer(JmsTemplate jmsTemplate, Destination destination) {
        this.jmsTemplate = jmsTemplate;
        this.destination = destination;
    }

    public void sendContactUpdate(final AddressDTO addressDTO) {
        jmsTemplate.send(destination, new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                return session.createObjectMessage(addressDTO);
            }
        });

    }
}
