import { Route, Routes } from "react-router";
import { LowerThirdPopoutPage } from "./LowerThird";

export function DashboardRoutes() {
    return <Routes>
        <Route path="lowerThird/:hookId" element={<LowerThirdPopoutPage />} />
    </Routes>;
}
