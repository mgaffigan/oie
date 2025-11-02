import { useState, useRef } from 'react';
import { PageHookRegistration, usePluginMount } from "../../services/PluginRegistry";
import type { OieDynamicMenuItem } from 'oieshell-common';
import { SettingsPageLayout } from './SettingsPageLayout';
import { DynamicMenu } from '../../layout/MenuBar';

export function PluginSettingsTab(props: { hook: PageHookRegistration, allTabs: Array<PageHookRegistration> }) {
    const [menuItems, setMenuItems] = useState<Array<OieDynamicMenuItem>>([]);
    const mountRef = useRef<HTMLDivElement>(null);

    usePluginMount(mountRef, async (target) => {
        const mountPlugin = await props.hook.loadAsync();
        return mountPlugin(target, setMenuItems);
    }, [props.hook]);

    return (
        <SettingsPageLayout title={props.hook.header} commands={<DynamicMenu menuItems={menuItems} defaultTitle={props.hook.header} />} allTabs={props.allTabs}>
            <div ref={mountRef} style={{ height: '100%' }} />
        </SettingsPageLayout>
    );
}
