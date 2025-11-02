import { useState, useRef } from "react";
import { PageHookRegistration, usePluginMount } from "../../services/PluginRegistry";
import { StandardLayout } from "../../layout/StandardLayout";
import { DynamicMenu } from "../../layout/MenuBar";
import type { OieDynamicMenuItem } from "oieshell-common";

export function PluginMainPage(props: { hook: PageHookRegistration }) {
    const [menuItems, setMenuItems] = useState<Array<OieDynamicMenuItem>>([]);
    const mountRef = useRef<HTMLDivElement>(null);

    usePluginMount(mountRef, async (target) => {
        const mountPlugin = await props.hook.loadAsync();
        return mountPlugin(target, setMenuItems);
    }, [props.hook]);

    return <StandardLayout title={props.hook.header} commands={<DynamicMenu menuItems={menuItems} defaultTitle={props.hook.header} />}>
        <div ref={mountRef} />
    </StandardLayout>;
}
