import { useMemo } from "react";
import { Route, Routes } from "react-router";
import { useSession } from "../../services/Session";
import { PluginSettingsTab } from "./PluginSettingsTab";

export function SettingsRoutes() {
    const sess = useSession();
    const settingsTabs = useMemo(() => sess.plugins.getPageHooks('SettingsTab'), [sess.plugins]);

    return <Routes>
        {settingsTabs.map(tab =>
            <Route key={tab.hookId} path={tab.hookId} element={<PluginSettingsTab hook={tab} allTabs={settingsTabs} />} />
        )}
    </Routes>;
}
