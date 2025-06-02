import * as yup from 'yup';
import { getLatestReleases } from './github.js';

export async function postNotifications(req, res) {
    const notifications = await getLatestReleases();

    if (req.body.op === 'getNotificationCount') {
        return res.status(200).json(notifications.map(rel => rel.id));
    } else if (req.body.op === 'getNotifications') {
        return res.status(200).json(notifications.map(rel => ({
            id: rel.id,
            name: rel.name,
            content: makeFullPageFromDiv(rel.body_html, rel.name),
            date: rel.published_at,
        })));
    } else {
        return res.status(400).json({
            error: 'Invalid operation',
        });
    }
}

function makeFullPageFromDiv(div, name) {
    name = htmlEncode(name);
    return `<html>
<head>
    <title>${name}</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.6/dist/css/bootstrap.min.css" rel="stylesheet" integrity="sha384-4Q6Gf2aSP4eDXB8Miphtr37CMZZQ5oXLH2yaXMJ2w8e2ZtHTl7GptT4jmndRuHDT" crossorigin="anonymous">
    <style>
        body {
            padding: 1em;
        }
    </style>
</head>
<body>
    <main>
        <h1>${name}</h1>
        <div>
            ${div}
        </div>
    </main>
</body>
</html>`;
}

function htmlEncode(text) {
    return text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

/*
POST https://connect.mirthcorp.com/NotificationServlet HTTP/1.1
Content-Type: application/x-www-form-urlencoded; charset=UTF-8
User-Agent: Apache-HttpClient/4.5.13 (Java/21.0.7)
Host: connect.mirthcorp.com
Content-Length: 2116

op=getNotificationCount&serverId=49885e13-4f2e-41a6-b66a-8e65f5492d9a&version=4.5.2&extensionVersions=url_encode_json
*/
export const notificationBodySchema = yup.object({
    op: yup.string().oneOf(['getNotificationCount', 'getNotifications']).required(),
    serverId: yup.string().required(),
    version: yup.string().required(),
    extensionVersions: yup.string().required(),
}).required();

/*
extensionVersions is a JSON object with the version of each extension:
{
    "Server Log":"4.5.2",
    "File Writer":"4.5.2",
    ...
}
*/
