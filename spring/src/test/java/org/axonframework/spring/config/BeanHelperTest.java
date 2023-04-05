package org.axonframework.spring.config;

import org.axonframework.config.Configuration;
import org.axonframework.modelling.command.Repository;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BeanHelperTest {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void verifyRetrievesRepositoryFromConfiguration() {
        Configuration configuration = mock(Configuration.class);
        Repository mockRepository = mock(Repository.class);
        when(configuration.repository(any())).thenReturn(mockRepository);

        Repository<?> actual = BeanHelper.repository(Random.class, configuration);

        verify(configuration).repository(Random.class);
        assertSame(mockRepository, actual);
    }
}