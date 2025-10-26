import { useEffect, useState, useRef } from "react";
import { PageHookRegistration } from "../../services/PluginRegistry";
import { useQuery } from "@tanstack/react-query";
import { StandardLayout } from "../../layout/StandardLayout";
import { DynamicMenu } from "../../layout/MenuBar";
import type { OieDynamicMenuItem } from "oieshell-common";

export function PluginMainPage(props: { hook: PageHookRegistration }) {
    const [menuItems, setMenuItems] = useState<Array<OieDynamicMenuItem>>([]);
    const mountRef = useRef<HTMLDivElement>(null);
    const { isPending, data: mountPlugin } = useQuery({
        queryKey: ["pluginMainPage", props.hook.hookId],
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
    }, [mountPlugin]);

    return <StandardLayout title={props.hook.header} commands={<DynamicMenu menuItems={menuItems} defaultTitle={props.hook.header} />}>
        <div ref={mountRef} style={{ height: '100%' }} />
    </StandardLayout>;
}