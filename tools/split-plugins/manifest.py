"""What the split moves and generates. split.py carries it out; nothing here is computed.

A move is (source directory, destination directory[, names]): the whole tree under the source, or only the
named files directly in it, keep their path relative to the source. Inside a plugin the destination may be
'common', 'server' or 'client': plugins/<plugin>/<tier>/ followed by the source path without its first
segment (server/ or client/). No file may be moved twice.

A project is plugins/<plugin>/<tier>/build.gradle: the templates, in order, each with its parameters.
A parameter that is a list repeats the template line that names it; see templates/.
"""

MOVES = [
    ('server', 'distribution', [
        'custom_manifest.mf', 'keystore.jks', 'keystore.properties']),
]

COPIES = [
    ('server/build.gradle', 'distribution/build.gradle'),
]

PLUGINS = {
    'dashboardstatus': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/dashboardstatus', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/dashboardstatus', 'common', [
                'ConnectionLogItem.java', 'DashboardConnectorStatusServletInterface.java']),
            ('server/src/main/java/com/mirth/connect/plugins/dashboardstatus', 'server', [
                'ConnectionStateItem.java', 'ConnectionStatusLogController.java',
                'DashboardConnectorEventListener.java', 'DashboardConnectorStatusMonitor.java',
                'DashboardConnectorStatusServlet.java', 'DefaultConnectionLogController.java']),
            ('server/src/main/resources/com/mirth/connect/plugins/dashboardstatus', 'plugins/dashboardstatus'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['dashboardstatus:common']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['dashboardstatus:common']}),
            ],
        },
    },
    'datapruner': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/datapruner', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/datapruner', 'common', [
                'DataPrunerServletInterface.java']),
            ('server/src/main/java/com/mirth/connect/plugins/datapruner', 'server', [
                'DataPruner.java', 'DataPrunerController.java', 'DataPrunerException.java',
                'DataPrunerInterface.java', 'DataPrunerJob.java', 'DataPrunerService.java',
                'DataPrunerServlet.java', 'DataPrunerStatus.java', 'DefaultDataPrunerController.java']),
            ('server/src/main/resources/com/mirth/connect/plugins/datapruner', 'plugins/datapruner'),
            ('server/src/test/java/com/mirth/connect/plugins/datapruner', 'server'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['datapruner:common']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['datapruner:common']}),
            ],
        },
    },
    'datatype-delimited': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/datatypes/delimited', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/datatypes/delimited', 'common', [
                'DelimitedBatchAdaptor.java', 'DelimitedBatchAdaptorFactory.java',
                'DelimitedBatchProperties.java', 'DelimitedDataTypeDelegate.java',
                'DelimitedDataTypeProperties.java', 'DelimitedDeserializationProperties.java',
                'DelimitedReader.java', 'DelimitedSerializationProperties.java', 'DelimitedSerializer.java',
                'DelimitedXMLHandler.java']),
            ('server/src/main/java/com/mirth/connect/plugins/datatypes/delimited', 'server', [
                'DelimitedDataTypeServerPlugin.java']),
            ('server/src/main/resources/com/mirth/connect/plugins/datatypes/delimited', 'plugins/datatype-delimited'),
            ('server/src/test/java/com/mirth/connect/plugins/datatypes/delimited', 'server'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['datatype-delimited:common']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['datatype-delimited:common']}),
            ],
        },
    },
    'datatype-dicom': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/datatypes/dicom', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/datatypes/dicom', 'common', [
                'DICOMDataTypeDelegate.java', 'DICOMDataTypeProperties.java', 'DICOMReference.java',
                'DICOMSerializer.java', 'DICOMVocabulary.java']),
            ('server/src/main/java/com/mirth/connect/plugins/datatypes/dicom', 'server', [
                'DICOMDataTypeServerPlugin.java']),
            ('server/src/main/resources/com/mirth/connect/plugins/datatypes/dicom', 'plugins/datatype-dicom'),
            ('server/src/test/java/com/mirth/connect/plugins/datatypes/dicom', 'server'),
        ],
        'projects': {
            'common': [
                ('tier.gradle', {'tier': 'common'}),
                ('dependencies.gradle', {'compile': ['dicom:common'], 'vendored': ['dicom']}),
            ],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['datatype-dicom:common']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['datatype-dicom:common']}),
            ],
        },
    },
    'datatype-edi': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/datatypes/edi', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/datatypes/edi', 'common', [
                'CompositeType.java', 'DataType.java', 'EDIBatchAdaptor.java', 'EDIBatchAdaptorFactory.java',
                'EDIBatchProperties.java', 'EDIDataTypeDelegate.java', 'EDIDataTypeProperties.java',
                'EDIReader.java', 'EDISerializationProperties.java', 'EDISerializer.java',
                'EDIXMLHandler.java', 'ElementType.java', 'FormatType.java', 'LoopType.java',
                'ObjectFactory.java', 'SegmentType.java', 'TransactionType.java', 'UsageType.java',
                'ValidCodesType.java', 'X12Vocabulary.java']),
            ('server/src/main/java/com/mirth/connect/plugins/datatypes/edi', 'server', [
                'EDIDataTypeServerPlugin.java']),
            ('server/src/main/resources/com/mirth/connect/plugins/datatypes/edi', 'plugins/datatype-edi', [
                'edi-to-xml-mapping.xml', 'plugin.xml']),
            ('server/src/main/resources/com/mirth/connect/plugins/datatypes/edi/xml', 'common'),
            ('server/src/test/java/com/mirth/connect/plugins/datatypes/edi', 'server'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['datatype-edi:common']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['datatype-edi:common']}),
            ],
        },
    },
    'datatype-hl7v2': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/datatypes/hl7v2', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/datatypes/hl7v2', 'common', [
                'ER7BatchAdaptor.java', 'ER7BatchAdaptorFactory.java', 'ER7BatchStreamReader.java',
                'ER7Reader.java', 'ER7Serializer.java', 'HL7v2ACKGenerator.java', 'HL7v2AutoResponder.java',
                'HL7v2BatchProperties.java', 'HL7v2DataTypeDelegate.java', 'HL7v2DataTypeProperties.java',
                'HL7v2DeserializationProperties.java', 'HL7v2ResponseGenerationProperties.java',
                'HL7v2ResponseValidationProperties.java', 'HL7v2ResponseValidator.java',
                'HL7v2SerializationProperties.java', 'HL7v2Vocabulary.java', 'HL7v2XMLQuickParser.java',
                'XMLEncodedHL7Handler.java']),
            ('server/src/main/java/com/mirth/connect/plugins/datatypes/hl7v2', 'server', [
                'HL7v2DataTypeServerPlugin.java']),
            ('server/src/main/resources/com/mirth/connect/plugins/datatypes/hl7v2', 'plugins/datatype-hl7v2'),
            ('server/src/test/java/com/mirth/connect/plugins/datatypes/hl7v2', 'server'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['datatype-hl7v2:common']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['datatype-hl7v2:common']}),
            ],
        },
    },
    'datatype-hl7v3': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/datatypes/hl7v3', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/datatypes/hl7v3', 'common', [
                'HL7V3BatchAdaptor.java', 'HL7V3BatchAdaptorFactory.java', 'HL7V3BatchProperties.java',
                'HL7V3DataTypeDelegate.java', 'HL7V3DataTypeProperties.java',
                'HL7V3SerializationProperties.java', 'HL7V3Serializer.java']),
            ('server/src/main/java/com/mirth/connect/plugins/datatypes/hl7v3', 'server', [
                'HL7V3DataTypeServerPlugin.java']),
            ('server/src/main/resources/com/mirth/connect/plugins/datatypes/hl7v3', 'plugins/datatype-hl7v3'),
            ('server/src/test/java/com/mirth/connect/plugins/datatypes/hl7v3', 'server'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['datatype-hl7v3:common']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['datatype-hl7v3:common']}),
            ],
        },
    },
    'datatype-json': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/datatypes/json', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/datatypes/json', 'common', [
                'JSONBatchAdaptor.java', 'JSONBatchAdaptorFactory.java', 'JSONBatchProperties.java',
                'JSONDataTypeDelegate.java', 'JSONDataTypeProperties.java', 'JSONSerializer.java']),
            ('server/src/main/java/com/mirth/connect/plugins/datatypes/json', 'server', [
                'JSONDataTypeServerPlugin.java']),
            ('server/src/main/resources/com/mirth/connect/plugins/datatypes/json', 'plugins/datatype-json'),
            ('server/src/test/java/com/mirth/connect/plugins/datatypes/json', 'server'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['datatype-json:common']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['datatype-json:common']}),
            ],
        },
    },
    'datatype-ncpdp': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/datatypes/ncpdp', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/datatypes/ncpdp', 'common', [
                'NCPDPBatchAdaptor.java', 'NCPDPBatchAdaptorFactory.java', 'NCPDPBatchProperties.java',
                'NCPDPDataTypeDelegate.java', 'NCPDPDataTypeProperties.java',
                'NCPDPDeserializationProperties.java', 'NCPDPReader.java', 'NCPDPReference.java',
                'NCPDPSerializationProperties.java', 'NCPDPSerializer.java', 'NCPDPVocabulary.java',
                'NCPDPXMLHandler.java']),
            ('server/src/main/java/com/mirth/connect/plugins/datatypes/ncpdp', 'server', [
                'NCPDPDataTypeServerPlugin.java']),
            ('server/src/main/resources/com/mirth/connect/plugins/datatypes/ncpdp', 'plugins/datatype-ncpdp', [
                'plugin.xml']),
            ('server/src/main/resources/com/mirth/connect/plugins/datatypes/ncpdp', 'server', [
                'ncpdp.xsd', 'ncpdpD0.xsd']),
            ('server/src/test/java/com/mirth/connect/plugins/datatypes/ncpdp', 'server'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['datatype-ncpdp:common']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['datatype-ncpdp:common']}),
            ],
        },
    },
    'datatype-raw': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/datatypes/raw', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/datatypes/raw', 'common', [
                'RawBatchAdaptor.java', 'RawBatchAdaptorFactory.java', 'RawBatchProperties.java',
                'RawDataTypeDelegate.java', 'RawDataTypeProperties.java', 'RawSerializer.java']),
            ('server/src/main/java/com/mirth/connect/plugins/datatypes/raw', 'server', [
                'RawDataTypeServerPlugin.java']),
            ('server/src/main/resources/com/mirth/connect/plugins/datatypes/raw', 'plugins/datatype-raw'),
            ('server/src/test/java/com/mirth/connect/plugins/datatypes/raw', 'server'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['datatype-raw:common']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['datatype-raw:common']}),
            ],
        },
    },
    'datatype-xml': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/datatypes/xml', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/datatypes/xml', 'common', [
                'XMLBatchAdaptor.java', 'XMLBatchAdaptorFactory.java', 'XMLBatchProperties.java',
                'XMLDataTypeDelegate.java', 'XMLDataTypeProperties.java', 'XMLSerializationProperties.java',
                'XMLSerializer.java']),
            ('server/src/main/java/com/mirth/connect/plugins/datatypes/xml', 'server', [
                'XMLDataTypeServerPlugin.java']),
            ('server/src/main/resources/com/mirth/connect/plugins/datatypes/xml', 'plugins/datatype-xml'),
            ('server/src/test/java/com/mirth/connect/plugins/datatypes/xml', 'server'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['datatype-xml:common']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['datatype-xml:common']}),
            ],
        },
    },
    'destinationsetfilter': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/destinationsetfilter', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/destinationsetfilter', 'common'),
            ('server/src/main/resources/com/mirth/connect/plugins/destinationsetfilter', 'plugins/destinationsetfilter'),
            ('server/src/test/java/com/mirth/connect/plugins/destinationsetfilter', 'server'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'test': ['destinationsetfilter:common']}),
                ('no-jar.gradle', {'tier': 'server'}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['destinationsetfilter:common']}),
            ],
        },
    },
    'dicom': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/connectors/dimse', 'client'),
            ('server/lib/extensions/dimse', 'plugins/dicom/lib'),
            ('server/src/main/java/com/mirth/connect/connectors/dimse', 'common', [
                'DICOMDispatcherProperties.java', 'DICOMReceiverProperties.java']),
            ('server/src/main/java/com/mirth/connect/connectors/dimse', 'server', [
                'DICOMConfiguration.java', 'DICOMConfigurationUtil.java', 'DICOMDispatcher.java',
                'DICOMReceiver.java', 'DefaultDICOMConfiguration.java']),
            ('server/src/main/java/com/mirth/connect/model/converters', 'common', [
                'DICOMConverter.java']),
            ('server/src/main/java/com/mirth/connect/server/attachments/dicom', 'server'),
            ('server/src/main/java/com/mirth/connect/server/userutil', 'server', [
                'DICOMUtil.java']),
            ('server/src/main/java/com/mirth/connect/server/util', 'server', [
                'DICOMMessageUtil.java']),
            ('server/src/main/java/org/dcm4che2/tool/dcmrcv', 'server'),
            ('server/src/main/java/org/dcm4che2/tool/dcmsnd', 'server'),
            ('server/src/main/resources/com/mirth/connect/connectors/dimse', 'plugins/dicom'),
            ('server/src/test/java/com/mirth/connect/connectors/dimse', 'server'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['dicom:common'], 'compile_only': ['imagej']}),
                ('userutil-sources.gradle', {'plugin': 'dicom', 'packages': ['com/mirth/connect/server/userutil']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['dicom:common']}),
            ],
        },
    },
    'dicomviewer': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/dicomviewer', 'client'),
            ('server/src/main/resources/com/mirth/connect/plugins/dicomviewer', 'plugins/dicomviewer'),
        ],
        'projects': {
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'implementation': ['imagej']}),
            ],
        },
    },
    'directoryresource': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/directoryresource', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/directoryresource', 'common', [
                'DirectoryResourceProperties.java', 'DirectoryResourceServletInterface.java']),
            ('server/src/main/java/com/mirth/connect/plugins/directoryresource', 'server', [
                'DirectoryResourcePlugin.java', 'DirectoryResourceServlet.java']),
            ('server/src/main/resources/com/mirth/connect/plugins/directoryresource', 'plugins/directoryresource'),
            ('server/src/test/java/com/mirth/connect/plugins/directoryresource', 'server'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['directoryresource:common']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['directoryresource:common']}),
            ],
        },
    },
    'doc': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/connectors/doc', 'client'),
            ('server/src/main/java/com/mirth/connect/connectors/doc', 'common', [
                'DocumentConnectorServletInterface.java', 'DocumentDispatcherProperties.java',
                'PageSize.java', 'Unit.java']),
            ('server/src/main/java/com/mirth/connect/connectors/doc', 'server', [
                'DocumentConnectorServlet.java', 'DocumentDispatcher.java']),
            ('server/src/main/resources/com/mirth/connect/connectors/doc', 'plugins/doc'),
        ],
        'projects': {
            'common': [
                ('tier.gradle', {'tier': 'common'}),
                ('dependencies.gradle', {
                    'compile_only': ['itext', 'itext.rtf', 'openhtmltopdf.core', 'openhtmltopdf.pdfbox', 'graphics2d', 'fontbox', 'pdfbox', 'xmpbox', 'flying.saucer.core', 'flying.saucer.pdf'],
                }),
            ],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {
                    'compile': ['doc:common'],
                    'implementation': ['itext', 'itext.rtf', 'openhtmltopdf.core', 'openhtmltopdf.pdfbox', 'graphics2d', 'fontbox', 'pdfbox', 'xmpbox', 'flying.saucer.core', 'flying.saucer.pdf'],
                }),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {
                    'compile': ['doc:common'],
                    'compile_only': ['itext', 'itext.rtf', 'openhtmltopdf.core', 'openhtmltopdf.pdfbox', 'graphics2d', 'fontbox', 'pdfbox', 'xmpbox', 'flying.saucer.core', 'flying.saucer.pdf'],
                }),
            ],
        },
    },
    'file': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/connectors/file', 'client'),
            ('server/lib/extensions/file', 'plugins/file/lib'),
            ('server/src/main/java/com/mirth/connect/connectors/file', 'common', [
                'FTPSchemeProperties.java', 'FileAction.java', 'FileConnectorServletInterface.java',
                'FileDispatcherProperties.java', 'FileReceiverProperties.java', 'FileScheme.java',
                'S3SchemeProperties.java', 'SchemeProperties.java', 'SftpSchemeProperties.java',
                'SmbDialectVersion.java', 'SmbSchemeProperties.java']),
            ('server/src/main/java/com/mirth/connect/connectors/file', 'server', [
                'DefaultFileConfiguration.java', 'FileConfiguration.java', 'FileConnector.java',
                'FileConnectorException.java', 'FileConnectorServlet.java', 'FileDispatcher.java',
                'FileReceiver.java', 'FileSystemConnectionOptions.java']),
            ('server/src/main/java/com/mirth/connect/connectors/file/filesystems', 'server'),
            ('server/src/main/java/com/mirth/connect/connectors/file/filters', 'server'),
            ('server/src/main/resources/com/mirth/connect/connectors/file', 'plugins/file'),
            ('server/src/test/java/com/mirth/connect/connectors/file', 'server'),
        ],
        'projects': {
            'common': [
                ('tier.gradle', {'tier': 'common'}),
                ('dependencies.gradle', {'compile_only': ['jcifs.ng']}),
            ],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {
                    'compile': ['file:common'],
                    'test': ['datatype-hl7v2:common'],
                    'implementation': ['jcifs.ng'],
                }),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['file:common'], 'compile_only': ['jcifs.ng']}),
            ],
        },
    },
    'globalmapviewer': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/globalmapviewer', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/globalmapviewer', 'common', [
                'GlobalMapServletInterface.java']),
            ('server/src/main/java/com/mirth/connect/plugins/globalmapviewer', 'server', [
                'DefaultGlobalMapController.java', 'GlobalMapController.java', 'GlobalMapProvider.java',
                'GlobalMapServlet.java']),
            ('server/src/main/resources/com/mirth/connect/plugins/globalmapviewer', 'plugins/globalmapviewer'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['globalmapviewer:common']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['globalmapviewer:common']}),
            ],
        },
    },
    'http': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/connectors/http', 'client'),
            ('server/src/main/java/com/mirth/connect/connectors/http', 'common', [
                'HttpConnectorServletInterface.java', 'HttpDispatcherProperties.java',
                'HttpReceiverProperties.java', 'HttpStaticResource.java']),
            ('server/src/main/java/com/mirth/connect/connectors/http', 'server', [
                'BinaryContentTypeResolver.java', 'DefaultHttpConfiguration.java', 'HttpConfiguration.java',
                'HttpConnectorServlet.java', 'HttpDispatcher.java', 'HttpMessageConverter.java',
                'HttpReceiver.java', 'HttpRequestMessage.java']),
            ('server/src/main/java/com/mirth/connect/server/userutil', 'server', [
                'HTTPUtil.java']),
            ('server/src/main/resources/com/mirth/connect/connectors/http', 'plugins/http'),
            ('server/src/test/java/com/mirth/connect/connectors/http', 'server'),
            ('server/src/test/java/com/mirth/connect/userutil', 'server', [
                'HTTPUtilTest.java']),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['http:common', 'httpauth:common']}),
                ('userutil-sources.gradle', {'plugin': 'http', 'packages': ['com/mirth/connect/server/userutil']}),
                ('cycle-host.gradle', {
                    'members': 'http:server, httpauth:server',
                    'guest': ['httpauth/server'],
                    'excluded': ['com/mirth/connect/plugins/httpauth'],
                }),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['http:common']}),
            ],
        },
    },
    'httpauth': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/httpauth', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/httpauth', 'common', [
                'HttpAuthConnectorPluginProperties.java', 'NoneHttpAuthProperties.java']),
            ('server/src/main/java/com/mirth/connect/plugins/httpauth', 'server', [
                'AuthenticationResult.java', 'Authenticator.java', 'AuthenticatorProvider.java',
                'AuthenticatorProviderFactory.java', 'HttpAuthServicePlugin.java', 'RequestInfo.java']),
            ('server/src/main/java/com/mirth/connect/plugins/httpauth/basic', 'common', [
                'BasicHttpAuthProperties.java']),
            ('server/src/main/java/com/mirth/connect/plugins/httpauth/basic', 'server', [
                'BasicAuthenticator.java', 'BasicAuthenticatorProvider.java']),
            ('server/src/main/java/com/mirth/connect/plugins/httpauth/custom', 'common', [
                'CustomHttpAuthProperties.java']),
            ('server/src/main/java/com/mirth/connect/plugins/httpauth/custom', 'server', [
                'CustomAuthenticatorProvider.java']),
            ('server/src/main/java/com/mirth/connect/plugins/httpauth/digest', 'common', [
                'DigestHttpAuthProperties.java']),
            ('server/src/main/java/com/mirth/connect/plugins/httpauth/digest', 'server', [
                'DigestAuthenticator.java', 'DigestAuthenticatorProvider.java']),
            ('server/src/main/java/com/mirth/connect/plugins/httpauth/javascript', 'common', [
                'JavaScriptHttpAuthProperties.java']),
            ('server/src/main/java/com/mirth/connect/plugins/httpauth/javascript', 'server', [
                'JavaScriptAuthenticator.java', 'JavaScriptAuthenticatorProvider.java']),
            ('server/src/main/java/com/mirth/connect/plugins/httpauth/oauth2', 'common', [
                'OAuth2HttpAuthProperties.java']),
            ('server/src/main/java/com/mirth/connect/plugins/httpauth/oauth2', 'server', [
                'OAuth2Authenticator.java', 'OAuth2AuthenticatorProvider.java']),
            ('server/src/main/java/com/mirth/connect/plugins/httpauth/userutil', 'server'),
            ('server/src/main/resources/com/mirth/connect/plugins/httpauth', 'plugins/httpauth'),
            ('server/src/test/java/com/mirth/connect/plugins/httpauth/basic', 'server'),
            ('server/src/test/java/com/mirth/connect/plugins/httpauth/digest', 'server'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['httpauth:common']}),
                ('userutil-sources.gradle', {
                    'plugin': 'httpauth',
                    'packages': ['com/mirth/connect/plugins/httpauth/userutil'],
                }),
                ('cycle-guest.gradle', {'host': 'http:server', 'packages': ['com/mirth/connect/plugins/httpauth']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['http:common', 'httpauth:common']}),
            ],
        },
    },
    'imageviewer': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/imageviewer', 'client'),
            ('server/src/main/resources/com/mirth/connect/plugins/imageviewer', 'plugins/imageviewer'),
        ],
        'projects': {
            'client': [('tier.gradle', {'tier': 'client'})],
        },
    },
    'javascriptrule': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/javascriptrule', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/javascriptrule', 'common'),
            ('server/src/main/resources/com/mirth/connect/plugins/javascriptrule', 'plugins/javascriptrule'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['javascriptrule:common']}),
            ],
        },
    },
    'javascriptstep': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/javascriptstep', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/javascriptstep', 'common'),
            ('server/src/main/resources/com/mirth/connect/plugins/javascriptstep', 'plugins/javascriptstep'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['javascriptstep:common']}),
            ],
        },
    },
    'jdbc': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/connectors/jdbc', 'client'),
            ('server/src/main/java/com/mirth/connect/connectors/jdbc', 'common', [
                'Column.java', 'DatabaseConnectorServletInterface.java', 'DatabaseDispatcherProperties.java',
                'DatabaseReceiverProperties.java', 'Table.java']),
            ('server/src/main/java/com/mirth/connect/connectors/jdbc', 'server', [
                'CustomDriver.java', 'DatabaseConnectorServlet.java', 'DatabaseDispatcher.java',
                'DatabaseDispatcherDelegate.java', 'DatabaseDispatcherException.java',
                'DatabaseDispatcherQuery.java', 'DatabaseDispatcherScript.java', 'DatabaseReceiver.java',
                'DatabaseReceiverDelegate.java', 'DatabaseReceiverException.java',
                'DatabaseReceiverQuery.java', 'DatabaseReceiverScript.java', 'JdbcUtils.java']),
            ('server/src/main/java/com/mirth/connect/server/userutil', 'server', [
                'DatabaseConnectionFactory.java']),
            ('server/src/main/resources/com/mirth/connect/connectors/jdbc', 'plugins/jdbc'),
            ('server/src/test/java/com/mirth/connect/connectors/jdbc', 'server'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['jdbc:common']}),
                ('userutil-sources.gradle', {'plugin': 'jdbc', 'packages': ['com/mirth/connect/server/userutil']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['jdbc:common']}),
            ],
        },
    },
    'jms': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/connectors/jms', 'client'),
            ('server/src/main/java/com/mirth/connect/connectors/jms', 'common', [
                'JmsConnectorProperties.java', 'JmsConnectorServletInterface.java',
                'JmsDispatcherProperties.java', 'JmsReceiverProperties.java']),
            ('server/src/main/java/com/mirth/connect/connectors/jms', 'server', [
                'JmsClient.java', 'JmsConnectorException.java', 'JmsConnectorServlet.java',
                'JmsDispatcher.java', 'JmsReceiver.java']),
            ('server/src/main/resources/com/mirth/connect/connectors/jms', 'plugins/jms'),
            ('server/src/test/java/com/mirth/connect/connectors/jms', 'server'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['jms:common']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['jms:common']}),
            ],
        },
    },
    'js': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/connectors/js', 'client'),
            ('server/src/main/java/com/mirth/connect/connectors/js', 'common', [
                'JavaScriptDispatcherProperties.java', 'JavaScriptReceiverProperties.java']),
            ('server/src/main/java/com/mirth/connect/connectors/js', 'server', [
                'JavaScriptDispatcher.java', 'JavaScriptReceiver.java']),
            ('server/src/main/resources/com/mirth/connect/connectors/js', 'plugins/js'),
            ('server/src/test/java/com/mirth/connect/connectors', 'server', [
                'ConnectorTests.java']),
            ('server/src/test/java/com/mirth/connect/connectors/js', 'server'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['js:common']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['js:common']}),
            ],
        },
    },
    'mapper': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/mapper', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/mapper', 'common'),
            ('server/src/main/resources/com/mirth/connect/plugins/mapper', 'plugins/mapper'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['mapper:common']}),
            ],
        },
    },
    'messagebuilder': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/messagebuilder', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/messagebuilder', 'common'),
            ('server/src/main/resources/com/mirth/connect/plugins/messagebuilder', 'plugins/messagebuilder'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['messagebuilder:common']}),
            ],
        },
    },
    'mllpmode': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/mllpmode', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/mllpmode', 'common', [
                'MLLPModeProperties.java']),
            ('server/src/main/java/com/mirth/connect/plugins/mllpmode', 'server', [
                'MLLPModeProvider.java', 'MLLPv2StreamHandler.java', 'MLLPv2StreamHandlerException.java']),
            ('server/src/main/resources/com/mirth/connect/plugins/mllpmode', 'plugins/mllpmode'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['mllpmode:common']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['mllpmode:common']}),
            ],
        },
    },
    'pdfviewer': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/pdfviewer', 'client'),
            ('server/lib/extensions/pdfviewer', 'plugins/pdfviewer/lib'),
            ('server/src/main/resources/com/mirth/connect/plugins/pdfviewer', 'plugins/pdfviewer'),
        ],
        'projects': {
            'client': [('tier.gradle', {'tier': 'client'})],
        },
    },
    'rulebuilder': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/rulebuilder', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/rulebuilder', 'common'),
            ('server/src/main/resources/com/mirth/connect/plugins/rulebuilder', 'plugins/rulebuilder'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['rulebuilder:common']}),
            ],
        },
    },
    'scriptfilerule': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/scriptfilerule', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/scriptfilerule', 'common'),
            ('server/src/main/resources/com/mirth/connect/plugins/scriptfilerule', 'plugins/scriptfilerule'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['scriptfilerule:common']}),
            ],
        },
    },
    'scriptfilestep': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/scriptfilestep', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/scriptfilestep', 'common'),
            ('server/src/main/resources/com/mirth/connect/plugins/scriptfilestep', 'plugins/scriptfilestep'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['scriptfilestep:common']}),
            ],
        },
    },
    'serverlog': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/serverlog', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/serverlog', 'common', [
                'ServerLogItem.java', 'ServerLogServletInterface.java']),
            ('server/src/main/java/com/mirth/connect/plugins/serverlog', 'server', [
                'ArrayAppender.java', 'DefaultServerLogController.java', 'ServerLogController.java',
                'ServerLogProvider.java', 'ServerLogServlet.java']),
            ('server/src/main/resources/com/mirth/connect/plugins/serverlog', 'plugins/serverlog'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['serverlog:common']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['serverlog:common']}),
            ],
        },
    },
    'smtp': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/connectors/smtp', 'client'),
            ('server/src/main/java/com/mirth/connect/connectors/smtp', 'common', [
                'Attachment.java', 'SmtpConnectorServletInterface.java', 'SmtpDispatcherProperties.java']),
            ('server/src/main/java/com/mirth/connect/connectors/smtp', 'server', [
                'DefaultSmtpConfiguration.java', 'SmtpConfiguration.java', 'SmtpConnectorServlet.java',
                'SmtpDispatcher.java']),
            ('server/src/main/resources/com/mirth/connect/connectors/smtp', 'plugins/smtp'),
            ('server/src/test/java/com/mirth/connect/connectors/smtp', 'server'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['smtp:common']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['smtp:common']}),
            ],
        },
    },
    'tcp': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/connectors/tcp', 'client'),
            ('server/src/main/java/com/mirth/connect/connectors/tcp', 'common', [
                'TcpConnectorServletInterface.java', 'TcpDispatcherProperties.java',
                'TcpReceiverProperties.java']),
            ('server/src/main/java/com/mirth/connect/connectors/tcp', 'server', [
                'DefaultTcpConfiguration.java', 'SocketUtil.java', 'StateAwareServerSocket.java',
                'StateAwareSocket.java', 'StateAwareSocketInterface.java', 'TcpConfiguration.java',
                'TcpConnectorServlet.java', 'TcpDispatcher.java', 'TcpReceiver.java',
                'TcpServicePlugin.java']),
            ('server/src/main/resources/com/mirth/connect/connectors/tcp', 'plugins/tcp'),
            ('server/src/test/java/com/mirth/connect/connectors/tcp', 'server'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {
                    'compile': ['tcp:common'],
                    'test': ['datatype-hl7v2:common', 'datatype-hl7v2:server', 'mllpmode:common', 'mllpmode:server'],
                }),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['tcp:common']}),
            ],
        },
    },
    'textviewer': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/textviewer', 'client'),
            ('server/src/main/resources/com/mirth/connect/plugins/textviewer', 'plugins/textviewer'),
        ],
        'projects': {
            'client': [('tier.gradle', {'tier': 'client'})],
        },
    },
    'vm': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/connectors/vm', 'client'),
            ('server/src/main/java/com/mirth/connect/connectors/vm', 'common', [
                'VmDispatcherProperties.java', 'VmReceiverProperties.java']),
            ('server/src/main/java/com/mirth/connect/connectors/vm', 'server', [
                'VmDispatcher.java', 'VmReceiver.java']),
            ('server/src/main/resources/com/mirth/connect/connectors/vm', 'plugins/vm'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['vm:common']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['vm:common']}),
            ],
        },
    },
    'ws': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/connectors/ws', 'client'),
            ('server/lib/extensions/ws', 'plugins/ws/lib'),
            ('server/src/main/java/com/mirth/connect/connectors/ws', 'common', [
                'Binding.java', 'DefinitionServiceMap.java', 'WebServiceConnectorServletInterface.java',
                'WebServiceDispatcherProperties.java', 'WebServiceReceiverProperties.java']),
            ('server/src/main/java/com/mirth/connect/connectors/ws', 'server', [
                'AcceptMessage.java', 'DefaultAcceptMessage.java', 'DefaultWebServiceConfiguration.java',
                'LoggingSOAPHandler.java', 'SSLSocketFactoryWrapper.java', 'SchemaType.java',
                'WebServiceConfiguration.java', 'WebServiceConnectorServlet.java',
                'WebServiceDispatcher.java', 'WebServiceReceiver.java']),
            ('server/src/main/resources/com/mirth/connect/connectors/ws', 'plugins/ws'),
            ('server/src/test/java/com/mirth/connect/connectors/ws', 'server'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'server': [
                ('tier.gradle', {'tier': 'server'}),
                ('dependencies.gradle', {'compile': ['httpauth:common', 'httpauth:server', 'ws:common']}),
            ],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['ws:common']}),
            ],
        },
    },
    'xsltstep': {
        'moves': [
            ('client/src/main/java/com/mirth/connect/plugins/xsltstep', 'client'),
            ('server/src/main/java/com/mirth/connect/plugins/xsltstep', 'common'),
            ('server/src/main/resources/com/mirth/connect/plugins/xsltstep', 'plugins/xsltstep'),
        ],
        'projects': {
            'common': [('tier.gradle', {'tier': 'common'})],
            'client': [
                ('tier.gradle', {'tier': 'client'}),
                ('dependencies.gradle', {'compile': ['xsltstep:common']}),
            ],
        },
    },
}
