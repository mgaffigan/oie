import type { components } from "oieapi-types";

export interface SessionContextType {
    user: components["schemas"]["User"];
    serverSettings: components["schemas"]["PublicServerSettings"];
    prefs: Record<string, string>;
}
