// src/lib/auth.ts
export async function loginWithBasic(creds: { username: string, password: string }) {
    const r = await fetch('/api/users/_login', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            accept: 'application/json',
        },
        body: new URLSearchParams(creds),
    });
    
    const data = await r.json();
    const status = data['com.mirth.connect.model.LoginStatus'];
    if (!status || status.status !== 'SUCCESS') {
        throw new Error(status?.message || 'Login failed');
    }
}

export async function logout() {
    await fetch('/api/users/_logout', {
        method: 'POST',
    });
}
