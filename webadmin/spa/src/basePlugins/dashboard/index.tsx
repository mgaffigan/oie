import { createRoot } from "react-dom/client";
import type { BasePlugin } from "../index";
import { ServerLogTab } from "./ServerLogTab.tsx"
import type { ReactNode } from "react";
import { GlobalMapTab } from "./GlobalMapTab.tsx";
import { ConnectionLogTab } from "./ConnectionLogTab.tsx";
import type { OieMountEvents } from "oieshell-common";

function mount(rootNode: ReactNode, target: HTMLDivElement): OieMountEvents {
    const root = createRoot(target);
    root.render(rootNode);
    return {
        onUnmount() {
            root.unmount();
        }
    };
}

export default {
    manifest: {
        pluginId: "dashboard",
        displayName: "Dashboard",
        version: "base",
        description: "Provides the dashboard page with channel list and lower third sections.",
        factoryPath: "base",
        hooks: [
            {
                hookId: "dashboard-server-log",
                type: "DashboardLowerThird",
                header: "Server Log",
                order: 100,
            },
            {
                hookId: "dashboard-connection-log",
                type: "DashboardLowerThird",
                header: "Connection Log",
                order: 200,
            },
            {
                hookId: "dashboard-global-map",
                type: "DashboardLowerThird",
                header: "Global Map",
                order: 300,
            },
        ]
    },
    mountDashboardLowerThird(hookId, context): OieMountEvents {
        if (hookId === "dashboard-server-log") {
            return mount(<ServerLogTab context={context.pluginContext} />, context.target);
        } else if (hookId === "dashboard-connection-log") {
            return mount(<ConnectionLogTab context={context.pluginContext} />, context.target);
        } else if (hookId === "dashboard-global-map") {
            return mount(<GlobalMapTab  context={context.pluginContext} />, context.target);
        } else {
            throw new Error(`Unknown hook ID: ${hookId}`);
        }
    },
} as BasePlugin;
