package com.mirth.connect.server.api.providers;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.lang3.StringUtils;

import com.mirth.connect.server.api.DontRequireRequestedWith;

@Provider
@Priority(Priorities.AUTHENTICATION + 100)
public class RequestedWithFilter implements ContainerRequestFilter {

    @Context
    private ResourceInfo resourceInfo;

    private static boolean isRequestedWithHeaderRequired = true;

    // Jax requires a no-arg constructor to instantiate providers via classpath scanning.
    public RequestedWithFilter() {
    }

    public static void configure(PropertiesConfiguration mirthProperties) {
        isRequestedWithHeaderRequired = mirthProperties.getBoolean("server.api.require-requested-with", true);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!isRequestedWithHeaderRequired) {
            return;
        }

        // If the resource method or class is annotated with DontRequireRequestedWith, skip the check
        if (resourceInfo != null) {
            Method method = resourceInfo.getResourceMethod();
            if (method != null && method.getAnnotation(DontRequireRequestedWith.class) != null) {
                return;
            }
            Class<?> resourceClass = resourceInfo.getResourceClass();
            if (resourceClass != null && resourceClass.getAnnotation(DontRequireRequestedWith.class) != null) {
                return;
            }
        }
        
        List<String> header = requestContext.getHeaders().get("X-Requested-With");
        
        //if header is required and not present, send an error
        if (header == null || header.isEmpty() || StringUtils.isBlank(header.get(0))) {
            requestContext.abortWith(Response.status(400).entity("All requests must have 'X-Requested-With' header").build());
        }
    }
}
