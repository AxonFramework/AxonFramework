package org.axonframework.sample.app.command;

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.repository.Repository;
import org.axonframework.sample.app.api.ChangeContactNameCommand;
import org.axonframework.sample.app.api.ContactNameAlreadyTakenException;
import org.axonframework.sample.app.api.CreateContactCommand;
import org.axonframework.sample.app.query.ContactEntry;
import org.axonframework.sample.app.query.ContactRepository;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListener;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

/**
 * @author Jettro Coenradie
 */
public class ContactCommandHandlerTest {
    private ContactCommandHandler contactCommandHandler;
    @Mock
    private UnitOfWork mockUnitOfWork;
    @Mock
    private ContactNameRepository mockContactNameRepository;
    @Mock
    private Repository<Contact> mockRepository;
    @Mock
    private ContactRepository mockContactRepository;
    @Mock
    private Contact mockContact;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        contactCommandHandler = new ContactCommandHandler();
        contactCommandHandler.setContactNameRepository(mockContactNameRepository);
        contactCommandHandler.setRepository(mockRepository);
        contactCommandHandler.setContactRepository(mockContactRepository);
    }

    @Test
    public void testHandleCreateContactCommand_doubleName() throws Exception {
        CreateContactCommand command = new CreateContactCommand();
        command.setContactId(UUID.randomUUID().toString());
        command.setNewContactName("Double name");

        when(mockContactNameRepository.claimContactName("Double name")).thenReturn(false);

        try {
            contactCommandHandler.handle(command, mockUnitOfWork);
            fail("ContactNameAlreadyTakenException was expected to be thrown");
        } catch (ContactNameAlreadyTakenException e) {
            // as expected
        }

        verify(mockContactNameRepository).claimContactName("Double name");
    }

    @Test
    public void testHandleCreateContactCommand_otherProblemWithTransaction() throws Exception {
        CreateContactCommand command = new CreateContactCommand();
        command.setContactId(UUID.randomUUID().toString());
        command.setNewContactName("Good name");

        when(mockContactNameRepository.claimContactName("Good name")).thenReturn(true);

        contactCommandHandler.handle(command, mockUnitOfWork);

        ArgumentCaptor<UnitOfWorkListener> unitOfWorkListenerArgumentCaptor =
                ArgumentCaptor.forClass(UnitOfWorkListener.class);
        verify(mockContactNameRepository).claimContactName("Good name");
        verify(mockUnitOfWork).registerListener(unitOfWorkListenerArgumentCaptor.capture());

        unitOfWorkListenerArgumentCaptor.getValue().onRollback(new RuntimeException("Something went horribly wrong"));

        verify(mockContactNameRepository).cancelContactName("Good name");
    }

    @Test
    public void testHandleChangeNameContactCommand_doubleName() {
        ChangeContactNameCommand command = new ChangeContactNameCommand();
        command.setContactId(UUID.randomUUID().toString());
        command.setContactNewName("Double New Name");

        Contact contact = mock(Contact.class);

        when(mockContactNameRepository.claimContactName("Double New Name")).thenReturn(false);

        try {
            contactCommandHandler.handle(command, mockUnitOfWork);
            fail("ContactNameAlreadyTakenException was expected to be thrown");
        } catch (ContactNameAlreadyTakenException e) {
            // as expected
        }

        verify(mockContactNameRepository).claimContactName("Double New Name");
        verify(contact, never()).changeName("Double New Name");
    }

    @Test
    public void testHandleChangeNameContactCommand_happypath() {
        ChangeContactNameCommand command = new ChangeContactNameCommand();
        command.setContactId(UUID.randomUUID().toString());
        command.setContactNewName("Good New Name");

        ContactEntry mockContactEntry = mock(ContactEntry.class);

        when(mockContactNameRepository.claimContactName("Good New Name"))
                .thenReturn(true);
        when(mockRepository.load(isA(AggregateIdentifier.class)))
                .thenReturn(mockContact);
        when(mockContactRepository.loadContactDetails(command.getContactId()))
                .thenReturn(mockContactEntry);
        when(mockContactEntry.getName()).thenReturn("Good Old Name");

        ArgumentCaptor<UnitOfWorkListener> unitOfWorkListenerArgumentCaptor =
                ArgumentCaptor.forClass(UnitOfWorkListener.class);

        contactCommandHandler.handle(command, mockUnitOfWork);

        verify(mockContactNameRepository).claimContactName("Good New Name");
        verify(mockContact).changeName("Good New Name");
        verify(mockUnitOfWork, times(2)).registerListener(unitOfWorkListenerArgumentCaptor.capture());
        for (UnitOfWorkListener listener : unitOfWorkListenerArgumentCaptor.getAllValues()) {
            listener.afterCommit();
        }

        verify(mockContactNameRepository).cancelContactName("Good Old Name");
    }

    @Test
    public void testHandleChangeNameContactCommand_otherProblemWithTransaction() throws Exception {
        ChangeContactNameCommand command = new ChangeContactNameCommand();
        command.setContactId(UUID.randomUUID().toString());
        command.setContactNewName("Good New Name");

        when(mockContactNameRepository.claimContactName("Good New Name")).thenReturn(true);
        when(mockRepository.load(isA(AggregateIdentifier.class))).thenReturn(mockContact);

        contactCommandHandler.handle(command, mockUnitOfWork);

        ArgumentCaptor<UnitOfWorkListener> unitOfWorkListenerArgumentCaptor =
                ArgumentCaptor.forClass(UnitOfWorkListener.class);
        verify(mockContactNameRepository).claimContactName("Good New Name");
        verify(mockUnitOfWork, times(2)).registerListener(unitOfWorkListenerArgumentCaptor.capture());
        for (UnitOfWorkListener listener : unitOfWorkListenerArgumentCaptor.getAllValues()) {
            listener.onRollback(new RuntimeException("Something went horribly wrong"));
        }

        verify(mockContactNameRepository).cancelContactName("Good New Name");
        verify(mockContactNameRepository, never()).cancelContactName("Good Old Name");
    }

}
