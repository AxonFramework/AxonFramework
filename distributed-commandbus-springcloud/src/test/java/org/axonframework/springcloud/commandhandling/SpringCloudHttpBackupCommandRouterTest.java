/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.springcloud.commandhandling;

import com.google.common.collect.ImmutableList;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.mockito.runners.*;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SpringCloudHttpBackupCommandRouterTest {

    private static final int LOAD_FACTOR = 1;
    private static final String SERVICE_INSTANCE_ID = "SERVICE_ID";
    private static final URI SERVICE_INSTANCE_URI = URI.create("endpoint");
    private static final Predicate<? super CommandMessage<?>> COMMAND_NAME_FILTER = c -> true;

    private SpringCloudHttpBackupCommandRouter testSubject;

    @Mock
    private DiscoveryClient discoveryClient;
    @Mock
    private RoutingStrategy routingStrategy;
    @Mock
    private RestTemplate restTemplate;
    private String messageRoutingInformationEndpoint = "/message-routing-information";
    @Mock
    private ServiceInstance serviceInstance;

    private MessageRoutingInformation expectedMessageRoutingInfo;
    @Captor
    private ArgumentCaptor<URI> uriArgumentCaptor;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        when(serviceInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(serviceInstance.getUri()).thenReturn(SERVICE_INSTANCE_URI);
        when(serviceInstance.getMetadata()).thenReturn(new HashMap<>());

        when(discoveryClient.getServices()).thenReturn(Collections.singletonList(SERVICE_INSTANCE_ID));
        when(discoveryClient.getInstances(SERVICE_INSTANCE_ID)).thenReturn(Collections.singletonList(serviceInstance));
        when(discoveryClient.getLocalServiceInstance()).thenReturn(serviceInstance);

        expectedMessageRoutingInfo =
                new MessageRoutingInformation(LOAD_FACTOR, COMMAND_NAME_FILTER, new XStreamSerializer());

        ResponseEntity<MessageRoutingInformation> responseEntity = mock(ResponseEntity.class);
        when(responseEntity.hasBody()).thenReturn(true);
        when(responseEntity.getBody()).thenReturn(expectedMessageRoutingInfo);
        when(restTemplate.exchange(
                any(), eq(HttpMethod.GET), eq(HttpEntity.EMPTY), eq(MessageRoutingInformation.class)
        )).thenReturn(responseEntity);

        testSubject = new SpringCloudHttpBackupCommandRouter(discoveryClient,
                                                             routingStrategy,
                                                             restTemplate,
                                                             messageRoutingInformationEndpoint);
    }

    @Test
    public void testGetLocalMessageRoutingInformationReturnsNullIfMembershipIsNeverUpdated() throws Exception {
        assertNull(testSubject.getLocalMessageRoutingInformation());
    }

    @Test
    public void testGetLocalMessageRoutingInformationReturnsMessageRoutingInformation() throws Exception {
        testSubject.updateMembership(LOAD_FACTOR, COMMAND_NAME_FILTER);

        MessageRoutingInformation result = testSubject.getLocalMessageRoutingInformation();

        assertEquals(expectedMessageRoutingInfo, result);
    }

    @Test
    public void testGetMessageRoutingInformationReturnsLocalMessageRoutingInformationIfSimpleMemberIsLocal()
            throws Exception {
        testSubject.updateMembership(LOAD_FACTOR, COMMAND_NAME_FILTER);
        //TODO Still needs a fix
        Optional<MessageRoutingInformation> result = testSubject.getMessageRoutingInformation(serviceInstance);

        assertTrue(result.isPresent());
        assertEquals(expectedMessageRoutingInfo, result.get());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetMessageRoutingInformationThrowsIllegalArgumentExceptionIfEndpointIsMissing() throws Exception {
        ServiceInstance remoteInstance = mock(ServiceInstance.class);
        when(remoteInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(remoteInstance.getUri()).thenReturn(null);

        testSubject.getMessageRoutingInformation(remoteInstance);
    }

    @Test
    public void testGetMessageRoutingInformationRequestsMessageRoutingInformation() throws Exception {
        ServiceInstance remoteInstance = mock(ServiceInstance.class);
        when(remoteInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(remoteInstance.getUri()).thenReturn(URI.create("http://remote"));

        Optional<MessageRoutingInformation> result = testSubject.getMessageRoutingInformation(remoteInstance);

        assertTrue(result.isPresent());
        assertEquals(expectedMessageRoutingInfo, result.get());

        verify(restTemplate).exchange(uriArgumentCaptor.capture(),
                                      eq(HttpMethod.GET),
                                      eq(HttpEntity.EMPTY),
                                      eq(MessageRoutingInformation.class));

        URI resultUri = uriArgumentCaptor.getValue();
        assertEquals(messageRoutingInformationEndpoint, resultUri.getPath());
    }

    @Test
    public void testUpdateMembershipsOnHeartbeatEventRequestsMessageRoutingInformationByHttpRequest() throws Exception {
        testSubject.updateMembership(LOAD_FACTOR, COMMAND_NAME_FILTER);

        ServiceInstance remoteInstance = mock(ServiceInstance.class);
        when(remoteInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(remoteInstance.getUri()).thenReturn(URI.create("http://remote"));

        when(discoveryClient.getServices()).thenReturn(ImmutableList.of(SERVICE_INSTANCE_ID));
        when(discoveryClient.getInstances(SERVICE_INSTANCE_ID))
                .thenReturn(ImmutableList.of(serviceInstance, remoteInstance));

        testSubject.updateMemberships(mock(HeartbeatEvent.class));

        verify(discoveryClient).getServices();
        verify(discoveryClient).getInstances(SERVICE_INSTANCE_ID);
        verify(restTemplate).exchange(uriArgumentCaptor.capture(),
                                      eq(HttpMethod.GET),
                                      eq(HttpEntity.EMPTY),
                                      eq(MessageRoutingInformation.class));
    }
}
