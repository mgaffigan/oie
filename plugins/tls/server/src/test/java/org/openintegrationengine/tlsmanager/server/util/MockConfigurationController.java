/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.server.util;

import com.mirth.commons.encryption.Digester;
import com.mirth.commons.encryption.Encryptor;
import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.model.ChannelDependency;
import com.mirth.connect.model.ChannelMetadata;
import com.mirth.connect.model.ChannelTag;
import com.mirth.connect.model.DatabaseSettings;
import com.mirth.connect.model.DriverInfo;
import com.mirth.connect.model.EncryptionSettings;
import com.mirth.connect.model.PasswordRequirements;
import com.mirth.connect.model.PublicServerSettings;
import com.mirth.connect.model.ServerConfiguration;
import com.mirth.connect.model.ServerSettings;
import com.mirth.connect.model.UpdateSettings;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.util.ConfigurationProperty;
import com.mirth.connect.util.ConnectionTestResponse;
import org.apache.commons.configuration2.PropertiesConfiguration;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class MockConfigurationController extends ConfigurationController {
    @Override
    public void initializeSecuritySettings() {

    }

    @Override
    public void initializeDatabaseSettings() {

    }

    @Override
    public void migrateKeystore() {

    }

    @Override
    public void updatePropertiesConfiguration(PropertiesConfiguration propertiesConfiguration) {

    }

    @Override
    public Encryptor getEncryptor() {
        return null;
    }

    @Override
    public Digester getDigester() {
        return null;
    }

    @Override
    public String getDatabaseType() {
        return "";
    }

    @Override
    public String getServerId() {
        return "";
    }

    @Override
    public String getServerName() {
        return "";
    }

    @Override
    public String getServerTimezone(Locale locale) {
        return "";
    }

    @Override
    public Calendar getServerTime() {
        return null;
    }

    @Override
    public List<String> getAvailableCharsetEncodings() throws ControllerException {
        return List.of();
    }

    @Override
    public String getBaseDir() {
        return "";
    }

    @Override
    public String getConfigurationDir() {
        return "";
    }

    @Override
    public String getApplicationDataDir() {
        return "";
    }

    @Override
    public ServerSettings getServerSettings() throws ControllerException {
        return null;
    }

    @Override
    public EncryptionSettings getEncryptionSettings() throws ControllerException {
        return null;
    }

    @Override
    public DatabaseSettings getDatabaseSettings() throws ControllerException {
        return null;
    }

    @Override
    public void setServerSettings(ServerSettings serverSettings) throws ControllerException {

    }

    @Override
    public PublicServerSettings getPublicServerSettings() throws ControllerException {
        return null;
    }

    @Override
    public UpdateSettings getUpdateSettings() throws ControllerException {
        return null;
    }

    @Override
    public void setUpdateSettings(UpdateSettings updateSettings) throws ControllerException {

    }

    @Override
    public String generateGuid() {
        return "";
    }

    @Override
    public List<DriverInfo> getDatabaseDrivers() throws ControllerException {
        return List.of();
    }

    @Override
    public void setDatabaseDrivers(List<DriverInfo> list) throws ControllerException {

    }

    @Override
    public String getServerVersion() {
        return "";
    }

    @Override
    public String getBuildDate() {
        return "";
    }

    @Override
    public int getMaxInactiveSessionInterval() {
        return 0;
    }

    @Override
    public String[] getHttpsClientProtocols() {
        return new String[0];
    }

    @Override
    public String[] getHttpsServerProtocols() {
        return new String[] {
            "TLSv1.3", "TLSv1.2", "SSLv2Hello"
        };
    }

    @Override
    public String[] getHttpsCipherSuites() {
        return new String[] {
            "TLS_CHACHA20_POLY1305_SHA256", "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256", "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256", "TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_AES_256_GCM_SHA384", "TLS_AES_128_GCM_SHA256", "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", "TLS_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384", "TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384", "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384", "TLS_DHE_DSS_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", "TLS_RSA_WITH_AES_128_GCM_SHA256", "TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256", "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256", "TLS_DHE_DSS_WITH_AES_128_GCM_SHA256", "TLS_EMPTY_RENEGOTIATION_INFO_SCSV"
        };
    }

    @Override
    public boolean isStartupDeploy() {
        return false;
    }

    @Override
    public int getStatsUpdateInterval() {
        return 0;
    }

    @Override
    public Integer getRhinoLanguageVersion() {
        return 0;
    }

    @Override
    public int getStartupLockSleep() {
        return 0;
    }

    @Override
    public ServerConfiguration getServerConfiguration() throws ControllerException {
        return null;
    }

    @Override
    public void setServerConfiguration(ServerConfiguration serverConfiguration, boolean b, boolean b1) throws ControllerException {

    }

    @Override
    public PasswordRequirements getPasswordRequirements() {
        return null;
    }

    @Override
    public boolean isBypasswordEnabled() {
        return false;
    }

    @Override
    public boolean checkBypassword(String s) {
        return false;
    }

    @Override
    public int getStatus() {
        return 0;
    }

    @Override
    public int getStatus(boolean b) {
        return 0;
    }

    @Override
    public void setStatus(int i) {

    }

    @Override
    public Map<String, String> getConfigurationMap() {
        return Map.of();
    }

    @Override
    public Map<String, ConfigurationProperty> getConfigurationProperties() throws ControllerException {
        return Map.of();
    }

    @Override
    public void setConfigurationProperties(Map<String, ConfigurationProperty> map, boolean b) throws ControllerException {

    }

    @Override
    public Properties getPropertiesForGroup(String s, Set<String> set) {
        return null;
    }

    @Override
    public void removePropertiesForGroup(String s) {

    }

    @Override
    public String getProperty(String s, String s1) {
        return "";
    }

    @Override
    public void saveProperty(String s, String s1, String s2) {

    }

    @Override
    public void removeProperty(String s, String s1) {

    }

    @Override
    public String getResources() {
        return "";
    }

    @Override
    public void setResources(String s) {

    }

    @Override
    public Set<ChannelDependency> getChannelDependencies() {
        return Set.of();
    }

    @Override
    public void setChannelDependencies(Set<ChannelDependency> set) {

    }

    @Override
    public Map<String, ChannelMetadata> getChannelMetadata() {
        return Map.of();
    }

    @Override
    public void setChannelMetadata(Map<String, ChannelMetadata> map) {

    }

    @Override
    public ConnectionTestResponse sendTestEmail(Properties properties) throws Exception {
        return null;
    }

    @Override
    public void setChannelTags(Set<ChannelTag> set) {

    }

    @Override
    public Set<ChannelTag> getChannelTags() {
        return Set.of();
    }
}
