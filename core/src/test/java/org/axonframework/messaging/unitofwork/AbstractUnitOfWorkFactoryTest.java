package org.axonframework.messaging.unitofwork;

import org.axonframework.common.Registration;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.metadata.CorrelationDataProvider;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Rene de Waele
 */
public class AbstractUnitOfWorkFactoryTest {

    private StubUnitOfWorkFactory subject;

    @Before
    public void setUp() {
        subject = new StubUnitOfWorkFactory();
    }

    @Test
    public void testCorrelationDataProviderRegistration() {
        CorrelationDataProvider mockCorrelationDataProvider = mock(CorrelationDataProvider.class);
        Registration registration = subject.registerCorrelationDataProvider(mockCorrelationDataProvider);
        UnitOfWork<?> mockUnitOfWork = subject.createUnitOfWork(null);
        verify(mockUnitOfWork).registerCorrelationDataProvider(mockCorrelationDataProvider);
        registration.cancel();
        mockUnitOfWork = subject.createUnitOfWork(null);
        verify(mockUnitOfWork, never()).registerCorrelationDataProvider(any());
    }

    private static class StubUnitOfWorkFactory extends AbstractUnitOfWorkFactory<UnitOfWork<?>> {
        @Override
        protected UnitOfWork<?> doCreateUnitOfWork(Message<?> message) {
            return mock(UnitOfWork.class);
        }
    }

}