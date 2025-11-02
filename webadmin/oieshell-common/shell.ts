import type { ClientApi } from './client.ts';
import type { LocalizedString } from './localization.ts';

export type OieShellHook 
    = OiePageHook
    | OieLowerThirdHook;

export interface OieShellManifest {
    /** Unique identifier for the shell plugin */
    pluginId: string;

    /** Localized display name for the shell plugin */
    displayName: LocalizedString;

    /** Version of the shell plugin */
    version: string;

    /** Localized description for the shell plugin */
    description?: LocalizedString;

    /** Path to the factory mjs of the shell plugin */
    factoryPath: string;

    /** Array of hooks provided by the shell plugin */
    hooks: Array<OieShellHook>;
}

export type OieShellHookType 
    = 'MainPage' 
    | 'SettingsTab'
    | 'DashboardLowerThird';
    // | 'ChannelEditAction' 
    // | 'ChannelEditTab'
    // | 'FilterType'
    // | 'PreProcessorType'
    // | 'TransformerType'
    // | 'ResponseTransformerType'
    // | 'PostProcessorType'
    // | 'DeployedChannelAction'
    // | 'DeployedChannelTab'
    // | 'MessageAction'
    // | 'MessageTab'
    // | 'SourceConnectorType'
    // | 'DestinationConnectorType'
    // | 'FormatterType';

export interface OieShellHookBase {
    type: OieShellHookType;
    hookId: string;
}

export interface OiePageHook extends OieShellHookBase {
    type: 'MainPage' | 'SettingsTab';
    header: LocalizedString;
    iconPath?: string;
}

export interface OieLowerThirdHook extends OieShellHookBase {
    type: 'DashboardLowerThird';
    header: LocalizedString;
    order: number;
}

export interface OiePluginContext {
    baseUrl: string;
    client: ClientApi;
}

export interface OiePluginFactory {
    create: (pluginId: string, context: OiePluginContext) => Promise<OieShellPlugin>;
}

export interface OieDynamicMenuItem {
    category?: LocalizedString;
    title: LocalizedString;
    iconPath?: string;
    onClick: () => void;
}

export interface OieMountContext {
    target: HTMLDivElement;
    pluginContext: OiePluginContext;
}

export interface OiePageMountContext extends OieMountContext {
    setMenuItems: (items: Array<OieDynamicMenuItem>) => void;
}

export interface OieChannelMountContext extends OieMountContext {
    channelId: string;
}

export interface OieMountEvents {
    onUnmount?: () => void;
}

export interface OieShellPlugin {
    mountMainPage?: (hookId: string, context: OiePageMountContext) => OieMountEvents | undefined;
    invokeSettingsTab?: (hookId: string, context: OiePageMountContext) => OieMountEvents | undefined;
    mountDashboardLowerThird?: (hookId: string, context: OieMountContext) => OieMountEvents | undefined;
}
