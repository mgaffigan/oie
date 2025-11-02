import { CommandButton, CommandGroup } from "../../layout/MenuBar";
import { StandardLayout } from "../../layout/StandardLayout";
import css from "./DashboardPage.module.scss";
import refreshIcon from '../../assets/icons/arrow_refresh.png';
import { CHANNEL_LIST_QUERY_KEY, ChannelList } from "./ChannelList";
import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useMemo, useState } from "react";
import { useSession } from "../../services/Session";
import { Link } from "react-router";
import { LowerThirdBody } from "./LowerThirdPopoutPage";

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
            <ChannelList />
            <DashboardLowerThird />
        </div>
    </StandardLayout>;
}

const LOWER_THIRD_PREF_KEY = "dashboardLowerThirdTab";

function DashboardLowerThird() {
    const [selectedPageOrUndefined, setSelectedPage] = useState<string | undefined>(() => localStorage.getItem(LOWER_THIRD_PREF_KEY) ?? undefined);

    const sess = useSession();
    const pluginPages = useMemo(() => sess.plugins.getLowerThirdHooks(), [sess.plugins]);

    if (pluginPages.length === 0) {
        return null;
    }

    const selectedPage = pluginPages.find(p => p.hookId === selectedPageOrUndefined) ?? pluginPages[0];

    return (<div className={css.lowerThird}>
        <div className={css.tabStrip} role="tablist" aria-label="Status Tabs">
            {pluginPages.map(page => (
                <button
                    key={page.hookId} id={"dlt-tab-" + page.hookId}
                    role="tab" aria-selected={selectedPage === page} aria-controls={"dlt-tabpanel-" + page.hookId}
                    className={`${css.tabItem} ${selectedPage === page ? css.active : ''}`}
                    onClick={() => {
                        setSelectedPage(page.hookId);
                        localStorage.setItem(LOWER_THIRD_PREF_KEY, page.hookId);
                    }}
                >
                    {page.header}
                </button>
            ))}
            <div className={css.popOutDiv}>
                <Link to={`/dashboard/lowerThird/${encodeURIComponent(selectedPage.hookId)}`} target="_blank" title="Pop out this tab into its own window">↗</Link>
            </div>
        </div>
        <div className={`card p-2 ${css.tabPanel}`} role="tabpanel" id={"dlt-tabpanel-" + selectedPage.hookId} aria-labelledby={"dlt-tab-" + selectedPage.hookId}>
            <LowerThirdBody hook={selectedPage} />
        </div>
    </div>);
}
