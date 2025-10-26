import type { OieShellManifest, OieShellPlugin } from "oieshell-common";

export interface BasePlugin extends OieShellPlugin {
    manifest: OieShellManifest;
}

export const basePlugins: Array<BasePlugin> = [
];
