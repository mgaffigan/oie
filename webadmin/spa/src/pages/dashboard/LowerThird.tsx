import { useMemo, useRef, useState } from "react";
import { Link, useParams } from "react-router";
import { useSession } from "../../services/Session";
import { StandardLayout } from "../../layout/StandardLayout";
import { usePluginMount, type LowerThirdHookRegistration } from "../../services/PluginRegistry";
import css from './LowerThird.module.scss';

function LowerThirdBody(props: { hook: LowerThirdHookRegistration }) {
    const mountRef = useRef<HTMLDivElement>(null);

    usePluginMount(mountRef, async (target) => {
        const mountPlugin = await props.hook.loadAsync();
        return mountPlugin(target);
    }, [props.hook]);

    return <div ref={mountRef} className={css.mountPoint} />;
}

export function LowerThirdPopoutPage() {
    const { hookId } = useParams<{ hookId: string }>();
    const sess = useSession();

    const hook = sess.plugins.getLowerThirdHooks().find(h => h.hookId === hookId);
    if (!hook) {
        return <StandardLayout title="Lower Third">
            <p>Lower Third {hookId} not found.</p>
        </StandardLayout>;
    }
    
    return <StandardLayout title={hook.header}>
        <div className="card p-2" style={{ height: '100%' }}>
            <LowerThirdBody hook={hook} />
        </div>
    </StandardLayout>;
}

const LOWER_THIRD_PREF_KEY = "dashboardLowerThirdTab";

export function DashboardLowerThird() {
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
