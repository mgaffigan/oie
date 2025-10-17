import createClient from 'openapi-fetch';
import type { paths as EngineApi } from 'oieapi-types/index.d.ts';

const baseUrl = window.location.origin + '/api/';

export const Client = createClient<EngineApi, 'application/json'>({
    baseUrl: baseUrl
});

interface LoginCredentials {
    username: string;
    password: string;
}

export async function login(creds: LoginCredentials) {
    const { data, error } = await Client.POST('/users/_login', { 
        headers: { 
            Accept: 'application/json',
            'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: creds,
    });
    return data || error;
}
