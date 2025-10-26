import { getLocalizedString } from "oieshell-common/localization.ts";
import type { BasePlugin } from "../basePlugins/index";
import type { OieShellPlugin, OieShellManifest, OiePluginFactory, OieShellHook, OiePluginContext, OiePageHook, OieDynamicMenuItem, OieMountContext, OieMountEvents } from "oieshell-common/shell.ts";

interface HookWithSource extends OieShellHook {
    pluginId: string;
}

export class PageHookRegistration {
    private hook: HookWithSource & OiePageHook;
    private registry: PluginRegistry;

    constructor(hook: HookWithSource & OiePageHook, registry: PluginRegistry) {
        this.hook = hook;
        this.registry = registry;
    }

    get hookId() {
        return this.hook.hookId;
    }

    get header() {
        return getLocalizedString(this.hook.header);
    }

    get iconPath() {
        return this.hook.iconPath;
    }

    async loadAsync(): Promise<(target: HTMLDivElement, setMenuItems: (items: Array<OieDynamicMenuItem>) => void) => OieMountEvents> {
        const plugin = await this.registry.getPluginAsync(this.hook.pluginId);

        return (target: HTMLDivElement, setMenuItems: (items: Array<OieDynamicMenuItem>) => void): OieMountEvents => {
            if (!plugin.mountMainPage) {
                throw new Error(`Plugin ${this.hook.hookId} does not implement mountMainPage`);
            }

            const mountContext: OieMountContext = {
                target,
                pluginContext: this.registry.pluginContext,
                setMenuItems
            };
            return plugin.mountMainPage(this.hook.hookId, mountContext) || {};
        };
    }
}


export class PluginRegistry {
    private context: OiePluginContext;
    private manifests: Map<string, OieShellManifest> = new Map();
    private hooks: Array<HookWithSource> = [];
    private initializedPlugins: Map<string, OieShellPlugin> = new Map();

    constructor(context: OiePluginContext) {
        this.context = context;
    }

    get pluginContext() {
        return this.context;
    }

    addManifest(manifest: OieShellManifest): void {
        if (this.manifests.has(manifest.pluginId)) {
            throw new Error(`Plugin with id ${manifest.pluginId} already registered`);
        }
        this.manifests.set(manifest.pluginId, manifest);
        for (const hook of manifest.hooks) {
            this.hooks.push({ ...hook, pluginId: manifest.pluginId });
        }
    }

    addBasePlugin(plugin: BasePlugin): void {
        this.addManifest(plugin.manifest);
        this.initializedPlugins.set(plugin.manifest.pluginId, plugin);
    }
    
    async getPluginAsync(pluginId: string): Promise<OieShellPlugin> {
        // Check if the plugin is already loaded
        const existing = this.initializedPlugins.get(pluginId);
        if (existing) {
            return existing;
        }

        // Load if necessary
        const manifest = this.manifests.get(pluginId);
        if (!manifest) {
            throw new Error(`Plugin with id ${pluginId} not found`);
        }

        const module = await import(/* @vite-ignore */ manifest.factoryPath);
        const factory: OiePluginFactory = module.default;
        const plugin = await factory.create(pluginId, this.context);
        this.initializedPlugins.set(pluginId, plugin);
        return plugin;
    }

    private getHooksOfType<T extends OieShellHook>(type: T['type']): Array<HookWithSource & T> {
        return this.hooks.filter((hook): hook is HookWithSource & T => hook.type === type);
    }

    getPageHooks(type: OiePageHook['type']): PageHookRegistration[] {
        let results: PageHookRegistration[] = [];
        for (const hook of this.getHooksOfType<OiePageHook>(type)) {
            results.push(new PageHookRegistration(hook, this));
        }
        return results;
    }
}
