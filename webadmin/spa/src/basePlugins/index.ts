import type { OieShellManifest, OieShellPlugin } from "oieshell-common";
import DashboardPlugin from "./dashboard/index.tsx";

export interface BasePlugin extends OieShellPlugin {
    manifest: OieShellManifest;
}

export const basePlugins: Array<BasePlugin> = [
    DashboardPlugin
];
