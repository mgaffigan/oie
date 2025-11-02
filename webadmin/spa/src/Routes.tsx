import { useMemo } from "react";
import { HashRouter, Route, Routes } from "react-router";
import { DashboardPage } from "./pages/dashboard/DashboardPage";
import { PluginMainPage } from "./pages/pluginMainPage/PluginMainPage";
import { SettingsRoutes } from "./pages/settings/SettingsRoutes";
import { useSession } from "./services/Session";
import { DashboardRoutes } from "./pages/dashboard/DashboardRoutes";

export function MainRouter() {
    const sess = useSession();
    const pluginPages = useMemo(() => sess.plugins.getPageHooks('MainPage'), [sess.plugins]);

    return <HashRouter>
        <Routes>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/dashboard/*" element={<DashboardRoutes />} />
            <Route path="/settings/*" element={<SettingsRoutes />} />
            {pluginPages.map(page =>
                <Route key={page.hookId} path={`/plugin/${page.hookId}`} element={<PluginMainPage hook={page} />} />
            )}
        </Routes>
    </HashRouter>;
}
