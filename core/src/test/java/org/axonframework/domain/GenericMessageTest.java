package org.axonframework.domain;

import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Rene de Waele
 */
public class GenericMessageTest {

    private Map<String, ?> correlationData = MetaData.from(Collections.singletonMap("foo", "bar"));

    @Before
    public void setUp() throws Exception {
        UnitOfWork unitOfWork = mock(UnitOfWork.class);
        when(unitOfWork.getCorrelationData()).thenAnswer(invocation -> correlationData);
        CurrentUnitOfWork.set(unitOfWork);
    }

    @After
    public void tearDown() throws Exception {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.clear(CurrentUnitOfWork.get());
        }
    }

    @Test
    public void testCorrelationDataAddedToNewMessage() {
        assertEquals(correlationData, new HashMap<>(new GenericMessage<>(new Object()).getMetaData()));

        MetaData newMetaData = MetaData.from(Collections.singletonMap("whatever", new Object()));
        assertEquals(newMetaData.mergedWith(correlationData),
                     new GenericMessage<>(new Object(), newMetaData).getMetaData());
    }
}