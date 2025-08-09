package com.mirth.connect.plugins.webdemo;

import static com.mirth.connect.plugins.webdemo.WebDemoServletInterface.PERMISSION_USE;
import static com.mirth.connect.plugins.webdemo.WebDemoServletInterface.PLUGIN_POINT;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.server.controllers.ControllerFactory;

public class WebDemoServlet extends MirthServlet implements WebDemoServletInterface {

    private static final WebDemoService provider = (WebDemoService) ControllerFactory.getFactory().createExtensionController().getServicePlugins().get(PLUGIN_POINT);

    public WebDemoServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc, PLUGIN_POINT);
    }

    @Override
    public String ping() {
        return "pong: " + DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    }

    @Override
    public Map<String, String> echo(String message) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        result.put("message", message == null ? "" : message);
        result.put("serverTime", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        return result;
    }
}
