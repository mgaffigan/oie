import { useState, useRef, useEffect } from 'react';
import { PageHookRegistration } from "../../services/PluginRegistry";
import type { OieDynamicMenuItem } from 'oieshell-common';
import { useQuery } from '@tanstack/react-query';
import { SettingsPageLayout } from './SettingsPageLayout';
import { DynamicMenu } from '../../layout/MenuBar';

export function PluginSettingsTab(props: { hook: PageHookRegistration, allTabs: Array<PageHookRegistration> }) {
    const [menuItems, setMenuItems] = useState<Array<OieDynamicMenuItem>>([]);
    const mountRef = useRef<HTMLDivElement>(null);
    const { isPending, data: mountPlugin } = useQuery({
        queryKey: ["pluginSettingsTab", props.hook.hookId],
        queryFn: props.hook.loadAsync
    });

    if (isPending) {
        return <span>Loading...</span>;
    }

    useEffect(() => {
        if (!mountPlugin || !mountRef.current) return;

        const events = mountPlugin(mountRef.current, setMenuItems);
        return () => {
            if (events.onUnmount) {
                events.onUnmount();
            }
        };
    }, [mountPlugin, props.allTabs]);

    return (
        <SettingsPageLayout title={props.hook.header} commands={<DynamicMenu menuItems={menuItems} defaultTitle={props.hook.header} />} allTabs={props.allTabs}>
            <div ref={mountRef} style={{ height: '100%' }} />
        </SettingsPageLayout>
    );
}