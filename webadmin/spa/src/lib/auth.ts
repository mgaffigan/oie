// src/lib/auth.ts
export async function loginWithBasic(username: string, password: string) {
    const r = await fetch('/api/users/_login', {
        method: 'POST',
        headers: {
            // Authorization: 'Basic ' + btoa(`${username}:${password}`),
            'Content-Type': 'application/x-www-form-urlencoded',
            accept: 'application/json',
            'X-Requested-With': 'OIEUI',
        },
        body: new URLSearchParams({
            username,
            password,
        }),
        credentials: 'include',
    });
    if (!r.ok)
        throw new Error(
            await r.text().catch(() => `Login failed (${r.status})`),
        );
}

export async function logout() {
    await fetch('/api/users/_logout', {
        method: 'POST',
        credentials: 'include',
    });
}
