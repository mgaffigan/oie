/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.httpauth.oauth2;

import com.mirth.connect.connectors.http.DefaultHttpConfiguration;
import com.mirth.connect.connectors.http.HttpConfiguration;
import com.mirth.connect.donkey.server.channel.Connector;
import com.mirth.connect.plugins.httpauth.Authenticator;
import com.mirth.connect.plugins.httpauth.AuthenticatorProvider;
import com.mirth.connect.plugins.httpauth.HttpAuthConnectorPluginProperties;

public class OAuth2AuthenticatorProvider extends AuthenticatorProvider {

    private HttpConfiguration configuration;

    public OAuth2AuthenticatorProvider(Connector connector, HttpAuthConnectorPluginProperties properties) {
        super(connector, properties);
        configuration = new DefaultHttpConfiguration();
    }

    HttpConfiguration getHttpConfiguration() {
        return configuration;
    }

    @Override
    public Authenticator getAuthenticator() throws Exception {
        return new OAuth2Authenticator(this);
    }
}