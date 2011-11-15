package org.axonframework.commandhandling.annotation;

import org.axonframework.domain.Message;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;

import java.lang.annotation.Annotation;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class CurrentUnitOfWorkParameterResolverFactoryTest {

    private CurrentUnitOfWorkParameterResolverFactory testSubject;
    private Annotation[] noAnnotations = new Annotation[]{};

    @Before
    public void setUp() throws Exception {
        testSubject = new CurrentUnitOfWorkParameterResolverFactory();
    }

    @Test
    public void testCreateInstance() throws Exception {
        assertNull(testSubject.createInstance(noAnnotations, Object.class, noAnnotations));
        assertSame(testSubject, testSubject.createInstance(noAnnotations, UnitOfWork.class, noAnnotations));
    }

    @Test
    public void testResolveParameterValue() throws Exception {
        DefaultUnitOfWork.startAndGet();
        try {
            assertSame(CurrentUnitOfWork.get(), testSubject.resolveParameterValue(mock(CommandMessage.class)));
        } finally {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testMatches() throws Exception {
        assertFalse(testSubject.matches(mock(CommandMessage.class)));
        DefaultUnitOfWork.startAndGet();
        try {
            assertFalse(testSubject.matches(mock(Message.class)));
            assertTrue(testSubject.matches(mock(CommandMessage.class)));
        } finally {
            CurrentUnitOfWork.get().rollback();
        }
    }
}
