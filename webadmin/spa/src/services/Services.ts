import createClient from 'openapi-fetch';
import type { paths as EngineApi } from 'oieapi-types/index.d.ts';

export const baseUrl = window.location.origin + '/api/';

export const Client = createClient<EngineApi, 'application/mirthapi+json'>({
    baseUrl: baseUrl,
    headers: {
        'Accept': 'application/mirthapi+json',
    }
});

interface LoginCredentials {
    username: string;
    password: string;
}

export async function login(creds: LoginCredentials) {
    const resp = await Client.POST('/users/_login', {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: creds,
    });
    // Error responses must have both message and status properties
    // otherwise, we take the HTTP status as an error
    if (!resp.response.ok && !(resp.error?.message && resp.error?.status)) {
        throw new Error(`Error during login: ${resp.response.status}`);
    }
    return resp.data || resp.error;
}
