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
    const { data, error } = await Client.POST('/users/_login', {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: creds,
    });
    return data || error;
}
