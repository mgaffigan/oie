import { createContext, useContext } from "react";
import type { components } from "oieapi-types";

export type SessionContextType = {
    user: components["schemas"]["User"];
};

export const SessionContext = createContext<SessionContextType | null>(null);

export function useSession(): SessionContextType {
    const context = useContext(SessionContext);
    if (!context) {
        throw new Error("useSession must be used within a SessionProvider");
    }
    return context;
}
