import {} from "react";
import { HashRouter, Route, Routes } from "react-router";
import { DashboardPage } from "./pages/dashboard/DashboardPage";

export function MainRouter() {
    return <HashRouter>
        <Routes>
            <Route path="/" element={<DashboardPage />} />
        </Routes>
    </HashRouter>;
}