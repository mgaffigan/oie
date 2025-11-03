import { CommandButton, CommandGroup } from "../../layout/MenuBar";
import { StandardLayout } from "../../layout/StandardLayout";
import css from "./DashboardPage.module.scss";
import refreshIcon from '../../assets/icons/arrow_refresh.png';
import { CHANNEL_LIST_QUERY_KEY, DashboardList } from "./DashboardList";
import { useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";
import { DashboardLowerThird } from "./LowerThird";

export function DashboardPage() {
    const queryClient = useQueryClient();

    const refetch = useCallback(() => {
        queryClient.invalidateQueries({ queryKey: [CHANNEL_LIST_QUERY_KEY] });
    }, [queryClient]);

    const commands = <>
        <CommandGroup title="Dashboard Tasks">
            <CommandButton title="Refresh" icon={refreshIcon} onClick={refetch} />
        </CommandGroup>
    </>;

    return <StandardLayout title="Dashboard" commands={commands}>
        <div className={css.dashboard}>
            <DashboardList />
            <DashboardLowerThird />
        </div>
    </StandardLayout>;
}
