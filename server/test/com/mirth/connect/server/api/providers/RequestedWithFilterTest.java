// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: 2025 Mitch Gaffigan <mitch.gaffigan@comcast.net>
package com.mirth.connect.server.api.providers;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import com.mirth.connect.client.core.PropertiesConfigurationUtil;

import junit.framework.TestCase;

public class RequestedWithFilterTest extends TestCase {
    
    private PropertiesConfiguration mirthProperties = PropertiesConfigurationUtil.create();
    
    @Test
    //assert that if property is set to false, isRequestedWithHeaderRequired = false
    public void testConstructor() {
        mirthProperties.clearProperty("server.api.require-requested-with");
        RequestedWithFilter.configure(mirthProperties);
        assertEquals(true, RequestedWithFilter.isRequestedWithHeaderRequired());

        mirthProperties.setProperty("server.api.require-requested-with", "false");
        RequestedWithFilter.configure(mirthProperties);
        assertEquals(false, RequestedWithFilter.isRequestedWithHeaderRequired());
    }
    
    @Test
    //assert that HttpServletResponse.sendError() is called when X-Requested-With is required but not present 
    public void testDoFilterRequestedWithTrue() {
        
        mirthProperties.setProperty("server.api.require-requested-with", "true");
        RequestedWithFilter.configure(mirthProperties);

        ContainerRequestContext mockCtx = Mockito.mock(ContainerRequestContext.class);
        when(mockCtx.getHeaders()).thenReturn(new javax.ws.rs.core.MultivaluedHashMap<String, String>());

        try {
            RequestedWithFilter filter = new RequestedWithFilter();
            filter.filter(mockCtx);
            ArgumentCaptor<Response> responseCaptor = 
                ArgumentCaptor.forClass(Response.class);
            verify(mockCtx).abortWith(responseCaptor.capture());
            Response response = responseCaptor.getValue();
            assertEquals(400, response.getStatus());
            assertEquals("All requests must have 'X-Requested-With' header", response.getEntity());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Test
    //assert that HttpServletResponse.sendError() is NOT called when X-Requested-With is not required and not present 
    public void testDoFilterRequestedWithFalse() {
        
        mirthProperties.setProperty("server.api.require-requested-with", "false");
        RequestedWithFilter.configure(mirthProperties);

        ContainerRequestContext mockCtx = Mockito.mock(ContainerRequestContext.class);
        when(mockCtx.getHeaders()).thenReturn(new javax.ws.rs.core.MultivaluedHashMap<String, String>());

        try {
            RequestedWithFilter filter = new RequestedWithFilter();
            filter.filter(mockCtx);
            verify(mockCtx, never()).abortWith(ArgumentMatchers.any(javax.ws.rs.core.Response.class));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
