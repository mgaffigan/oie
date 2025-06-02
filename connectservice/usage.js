import * as yup from 'yup';
import { parseStringPromise } from 'xml2js';
import { client } from './elastic.js';

export async function postRegistration(req, res) {
  const userData = {
    serverId: req.body.serverId,
    version: req.body.version,
    user: (await parseStringPromise(req.body.user, { explicitArray: false }))?.user,
  };

  console.log(`Received registration for ${JSON.stringify(userData)}`);

  await client.index({
    index: 'registration',
    id: userData.serverId,
    body: userData,
  });

  res.sendStatus(204);
}

/*
POST http://localhost:3000/RegistrationServlet
Content-Type: application/x-www-form-urlencoded; charset=UTF-8
User-Agent: Apache-HttpClient/4.5.13 (Java/21.0.7)

serverId=49885e13-4f2e-41a6-b66a-8e65f5492d9a&version=4.5.2&user=user_xml_data
*/
export const registrationBodySchema = yup.object({
  serverId: yup.string().required(),
  version: yup.string().required(),
  user: yup.string().required(), // XML data as a string
}).required();

/*
registration data:
<user>
  <id>1</id>
  <username>admin</username>
  <email>admin@local</email>
  <firstName>admin</firstName>
  <lastName>admin</lastName>
  <organization>admin</organization>
  <description>asdg</description>
  <phoneNumber>+1 219-555-1212</phoneNumber>
  <industry>Clinic</industry>
  <lastLogin>
    <time>1748811423800</time>
    <timezone>Etc/UTC</timezone>
  </lastLogin>
  <strikeCount>0</strikeCount>
  <country>United States</country>
  <role>Consultant - Engineer</role>
  <userConsent>true</userConsent>
</user>
*/

export async function postUsage(req, res) {
  let usagePayload = JSON.parse(req.body.data);

  // data.serverSettings.defaultAdministratorBackgroundColor is not representable
  // in ElasticSearch and has a crazy amount of data - so kill it.
  delete usagePayload.serverSettings.defaultAdministratorBackgroundColor;

  const usageData = {
    serverId: req.body.serverId,
    version: req.body.version,
    server: req.body.server,
    data: usagePayload
  };

  console.log(`Received usage data ${JSON.stringify(usageData)}`);
  await client.index({
    index: 'usage',
    id: usageData.serverId,
    body: usageData,
  });

  res.sendStatus(204);
}

/*
POST http://localhost:3000/UsageStatisticsServlet
Content-Type: application/x-www-form-urlencoded; charset=UTF-8
User-Agent: Apache-HttpClient/4.5.13 (Java/21.0.7)

serverId=49885e13-4f2e-41a6-b66a-8e65f5492d9a&version=4.5.2&server=true&data=usage_xml_data
*/
export const usageBodySchema = yup.object({
  serverId: yup.string().required(),
  version: yup.string().required(),
  server: yup.boolean().required(),
  data: yup.string().required(),
}).required();

/*
usage data:
{
    "mirthVersion": "4.5.2",
    "serverId": "49885e13-4f2e-41a6-b66a-8e65f5492d9a",
    "databaseType": "derby",
    "serverSpecs": {
      "availableProcessors": 16,
      "osVersion": "5.15.167.4-microsoft-standard-WSL2",
      "javaVersion": "21.0.7",
      "osName": "Linux",
      "maxMemory": 268435456
    },
    "clientSpecs": {
      "javaVersion": "1.8.0_351"
    },
    "globalScripts": {
      "deployLines": 3,
      "preprocessorLines": 3,
      "undeployLines": 4,
      "postprocessorLines": 5
    },
    "channels": [],
    "invalidChannels": 0,
    "codeTemplateLibraries": [],
    "alerts": [],
    "plugins": [
      {
        "pluginPoint": "Server Log"
      },
      ...
    ],
    "pluginMetaData": [
      {
        "serverClasses": [
          {
            "name": "com.mirth.connect.plugins.serverlog.ServerLogProvider",
            "weight": 0,
            "conditionClass": null
          }
        ],
        "clientClasses": [
          {
            "name": "com.mirth.connect.plugins.serverlog.ServerLogClient",
            "weight": 120,
            "conditionClass": null
          }
        ],
        "name": "Server Log"
      },
      ...
    ],
    "connectorMetaData": [
      {
        "transformers": "",
        "protocol": "smtp",
        "serverClassName": "com.mirth.connect.connectors.smtp.SmtpDispatcher",
        "name": "SMTP Sender",
        "type": "DESTINATION",
        "clientClassName": "com.mirth.connect.connectors.smtp.SmtpSender",
        "sharedClassName": "com.mirth.connect.connectors.smtp.SmtpDispatcherProperties"
      },
      ...
    ],
    "serverSettings": {
      "clearGlobalMap": true,
      "smtpTimeout": "5000",
      "smtpAuth": false,
      "defaultMetaDataColumns": [ ... ],
      "defaultAdministratorBackgroundColor": { ... },
      "queueBufferSize": 1000,
      "smtpSecure": "0"
    },
    "updateSettings": {},
    "debugStats": [],
    "users": 1
  }
*/
