import { createContext, useContext } from "react";
import { Client, baseUrl } from "./Services";
import { PluginRegistry } from "./PluginRegistry.ts";
import type { SessionContextType } from "oieshell-common/session.ts";
import type { OiePluginContext } from "oieshell-common";
import { basePlugins } from "../basePlugins/index.ts";

export interface AppContext extends SessionContextType {
    plugins: PluginRegistry;
}

export const SessionContext = createContext<AppContext | null>(null);

export function useSession(): AppContext {
    const context = useContext(SessionContext);
    if (!context) {
        throw new Error("useSession must be used within a SessionProvider");
    }
    return context;
}

export async function createSession(): Promise<AppContext> {
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

    const pluginContext: OiePluginContext = {
        baseUrl: baseUrl,
        client: Client,
    };
    const pluginRegistry = new PluginRegistry(pluginContext);
    for (const basePlugin of basePlugins) {
        pluginRegistry.addBasePlugin(basePlugin);
    }

    return {
        user, serverSettings,
        prefs: prefs as Record<string, string>,
        plugins: pluginRegistry
    };
}

export async function logout() {
    await Client.POST('/users/_logout');
    window.location.reload();
}
