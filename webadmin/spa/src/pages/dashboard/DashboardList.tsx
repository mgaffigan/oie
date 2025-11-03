import { useCallback, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Client } from "../../services/Services";
import css from "./DashboardList.module.scss";
import styleIcon from '../../assets/icons/style.png';
import tagIcon from '../../assets/icons/tag_blue.png';
import serverDatabaseIcon from '../../assets/icons/server_database.png';
import serverIcon from '../../assets/icons/server.png';

export const CHANNEL_LIST_QUERY_KEY = 'channelList';

async function getDashboardData(prefs: FilterPreferences) {
    const { data: groups } = await Client.GET("/channelgroups");
    if (!groups) throw new Error("Failed to fetch channel groups");

    const { data: channels } = await Client.GET("/channels");
    if (!channels) throw new Error("Failed to fetch channels");

    const { data: stats } = await Client.GET("/channels/statistics");
    if (!stats) throw new Error("Failed to fetch channel statistics");

    const { data: tags } = await Client.GET("/server/channelTags");
    if (!tags) throw new Error("Failed to fetch tags");

    return { groups, channels, stats, tags };
}

const DASHBOARD_PREFS_KEY = "dashboardListPrefs";

type TagDisplayMode = 'Names' | 'Icons' | 'None';
type StatisticsDisplayMode = 'Current' | 'Lifetime';

interface FilterPreferences {
    textFilter: string;
    useGroups: boolean;
    statsMode: StatisticsDisplayMode;
    tagDisplayMode: TagDisplayMode;
}

function getPrefs(): FilterPreferences {
    const saved = localStorage.getItem(DASHBOARD_PREFS_KEY);
    if (saved) {
        return JSON.parse(saved) as FilterPreferences;
    }
    return {
        textFilter: '',
        useGroups: true,
        statsMode: 'Current' as StatisticsDisplayMode,
        tagDisplayMode: 'Icons' as TagDisplayMode,
    };
}

export function DashboardList() {
    const [prefs, setPrefsRaw] = useState<FilterPreferences>(getPrefs);
    const setPrefs = useCallback((newPrefs: Partial<FilterPreferences>) => {
        setPrefsRaw(p => {
            const newValue = { ...p, ...newPrefs };
            localStorage.setItem(DASHBOARD_PREFS_KEY, JSON.stringify(newValue));
            return newValue;
        });
    }, []);

    const { data: channels } = useQuery({
        queryKey: [CHANNEL_LIST_QUERY_KEY],
        queryFn: () => getDashboardData(prefs),
    });

    const toggleTagDisplayMode = (button: TagDisplayMode) => {
        if (prefs.tagDisplayMode === button) {
            setPrefs({ tagDisplayMode: 'None' });
        } else {
            setPrefs({ tagDisplayMode: button });
        }
    };

    if (!channels) {
        return <span>Loading...</span>;
    }

    return <div className={`card p-2 ${css.dashboardListCard}`}>
        <div className={css.dashboardList}>
            <pre style={{height:'100%', overflowY: 'auto', fontFamily: 'monospace', marginBottom: 0}}>{JSON.stringify(channels, null, 2)}</pre>
        </div>
        <div className={css.toolbar}>
            <form className="d-flex flex-row align-items-center" onSubmit={e => e.preventDefault()}>
                <label htmlFor="dashboard-search-box" className="text-nowrap me-2">
                    Filter:
                </label>
                <input id="dashboard-search-box" className="form-control d-inline-block" type="text" value={prefs.textFilter}
                    onChange={e => setPrefs({ textFilter: e.target.value })} />
                <button type="button" className="btn btn-outline" onClick={() => setPrefs({ textFilter: '' })} title="Clear Filter" aria-label="Clear Filter">
                    ✕
                </button>
            </form>

            <div>
                1 Groups, 1 Deployed Channels
            </div>

            <div style={{ marginLeft: 'auto' }}>
                <input type="radio" id="dashboard-stats-mode" value="Current" name="dashboard-stats-mode"
                    checked={prefs.statsMode === 'Current'} onChange={() => setPrefs({ statsMode: 'Current' })} />
                <label htmlFor="dashboard-stats-mode" className="ms-1 me-2">Current Statistics</label>

                <input type="radio" id="dashboard-stats-mode-lifetime" value="Lifetime" name="dashboard-stats-mode"
                    checked={prefs.statsMode === 'Lifetime'} onChange={() => setPrefs({ statsMode: 'Lifetime' })} />
                <label htmlFor="dashboard-stats-mode-lifetime" className="ms-1 me-2">Lifetime Statistics</label>
            </div>

            <div className={css.separator} />

            <button className={`btn btn-outline ${prefs.tagDisplayMode === 'Names' ? 'active' : ''}`} onClick={() => toggleTagDisplayMode('Names')}
                title="Toggle Tag Names" aria-label="Toggle Tag Names">
                <img src={styleIcon} aria-hidden="true" />
            </button>
            <button className={`btn btn-outline ${prefs.tagDisplayMode === 'Icons' ? 'active' : ''}`} onClick={() => toggleTagDisplayMode('Icons')}
                title="Toggle Tag Icons" aria-label="Toggle Tag Icons">
                <img src={tagIcon} aria-hidden="true" />
            </button>

            <div className={css.separator} />

            <button className={`btn btn-outline ${prefs.useGroups ? 'active' : ''}`} onClick={() => setPrefs({ useGroups: true })}
                title="Show Groups" aria-label="Show Groups">
                <img src={serverDatabaseIcon} aria-hidden="true" />
            </button>
            <button className={`btn btn-outline ${!prefs.useGroups ? 'active' : ''}`} onClick={() => setPrefs({ useGroups: false })}
                title="Hide Groups" aria-label="Hide Groups">
                <img src={serverIcon} aria-hidden="true" />
            </button>
        </div>
    </div>;
}
