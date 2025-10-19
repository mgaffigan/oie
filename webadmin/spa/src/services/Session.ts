import { createContext, useContext } from "react";
import type { components } from "oieapi-types";
import { Client } from "./Services";

export type SessionContextType = {
    user: components["schemas"]["User"];
    serverSettings: components["schemas"]["PublicServerSettings"];
    prefs: Record<string, string>;
};

export const SessionContext = createContext<SessionContextType | null>(null);

export function useSession(): SessionContextType {
    const context = useContext(SessionContext);
    if (!context) {
        throw new Error("useSession must be used within a SessionProvider");
    }
    return context;
}

export async function createSession(): Promise<SessionContextType> {
    const { data: user } = await Client.GET('/users/current');
    if (!user) {
        throw new Error('Not authenticated');
    }

    const { data: serverSettings } = await Client.GET('/server/publicSettings');
    if (!serverSettings) {
        throw new Error('Failed to fetch server settings');
    }

    const { data: prefs } = await Client.GET("/users/{userId}/preferences", { params: { path: { userId: user.id! } } });
    if (!prefs) {
        throw new Error('Failed to fetch user preferences');
    }

    return {
        user, serverSettings,
        prefs: prefs as Record<string, string>
    };
}

export async function logout() {
    await Client.POST('/users/_logout');
    window.location.reload();
}
