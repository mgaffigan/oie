import { writeFileSync } from 'fs';
import { execSync } from 'child_process';


// Run in node to update the OIE API type definitions
// Transforms OIE API type definitions into a format usable by tools

// fetch from http://localhost:8080/api/openapi.json to api_raw.json
const apiTextResp = await fetch('http://localhost:8080/api/openapi.json');
if (!apiTextResp.ok) {
    throw new Error(`Failed to fetch API definition: ${apiTextResp.status} ${apiTextResp.statusText}`);
}
const apiText = await apiTextResp.text();
const apis = JSON.parse(apiText);

const apiRawPath = 'api_raw.json';
const apiRawPretty = JSON.stringify(apis, null, 2);
writeFileSync(apiRawPath, apiRawPretty, 'utf-8');

// Strip examples that don't exist from the file
function stripExamples(obj) {
    if (typeof obj !== 'object' || obj === null) {
        return;
    }

    if ("examples" in obj) {
        delete obj.examples;
    }

    if ("responses" in obj) {
        // If only a default key exists, create a 2xx key with the same content
        const responses = obj.responses;
        if (Object.keys(responses).length === 1 && "default" in responses) {
            responses["2XX"] = responses["default"];
        }
    }

    for (const key in obj) {
        stripExamples(obj[key]);
    }
}
stripExamples(apis);



// Write the cleaned API definition to a file
const apiCleanedPath = 'api_cleaned.json';
const apiCleanedPretty = JSON.stringify(apis, null, 2);
writeFileSync(apiCleanedPath, apiCleanedPretty, 'utf-8');

// Call npx swagger-cli bundle to bundle the cleaned API definition
const bundledApiPath = 'api_bundled.json';
execSync(`npx @redocly/cli bundle "${apiCleanedPath}" -o "${bundledApiPath}"`, { stdio: 'inherit' });

// Call npx openapi-typescript to generate TypeScript types from the bundled API definition
const typesOutputPath = 'index.d.ts';
execSync(`npx openapi-typescript "${bundledApiPath}" --output "${typesOutputPath}"`, { stdio: 'inherit' });
