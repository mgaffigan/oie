package com.mirth.connect.plugins.directoryresource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.mirth.connect.model.ResourceProperties;
import com.mirth.connect.model.ResourcePropertiesList;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

/**
 * Core builds the default resource as XML so it need not load DirectoryResourceProperties;
 * this keeps that XML in step with the class.
 */
public class DefaultResourcePropertiesTest {

    @Test
    public void defaultElementDeserializesToTheDefaultDirectoryResource() {
        String xml = ResourceProperties.createDefaultResourcePropertiesElement();

        ResourcePropertiesList list = ObjectXMLSerializer.getInstance().deserialize(xml, ResourcePropertiesList.class);

        assertEquals(1, list.getList().size());
        assertTrue(list.getList().get(0) instanceof DirectoryResourceProperties);
        DirectoryResourceProperties resource = (DirectoryResourceProperties) list.getList().get(0);
        assertEquals(DirectoryResourceProperties.PLUGIN_POINT, resource.getPluginPointName());
        assertEquals(DirectoryResourceProperties.TYPE, resource.getType());
        assertEquals(ResourceProperties.DEFAULT_RESOURCE_ID, resource.getId());
        assertEquals(ResourceProperties.DEFAULT_RESOURCE_NAME, resource.getName());
        assertEquals("custom-lib", resource.getDirectory());
        assertTrue(resource.isIncludeWithGlobalScripts());
        assertTrue(resource.isDirectoryRecursion());
    }
}
