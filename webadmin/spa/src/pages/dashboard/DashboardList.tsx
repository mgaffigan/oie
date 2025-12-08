import { useQuery } from "@tanstack/react-query";
import { useReactTable, getCoreRowModel, getExpandedRowModel, flexRender, type ColumnDef, type Row, getSortedRowModel } from "@tanstack/react-table";
import { getDashboardData, type DashboardRowModel, type ChannelStateOrMixed } from "./DashboardData";
import { type FilterPreferences, type TagDisplayMode, DASHBOARD_PREFS_KEY } from "./DashboardPrefs";
import css from "./DashboardList.module.scss";
import styleIcon from '../../assets/icons/style.png';
import tagIcon from '../../assets/icons/tag_blue.png';
import serverDatabaseIcon from '../../assets/icons/server_database.png';
import serverIcon from '../../assets/icons/server.png';
import { usePreferences } from "../../services/Preferences";
import { useSession } from "../../services/Session";
import type { ReactNode } from "react";

export const CHANNEL_LIST_QUERY_KEY = 'channelList';

function getRowPrefixes(row: Row<DashboardRowModel>, inverseDepth = 0): Array<ReactNode> {
    if (!row.getParentRow()) {
        return [];
    }

    const siblings = row.getParentRow()!.subRows;
    const isLast = siblings.indexOf(row) === siblings.length - 1;

    const parentPrefixes = getRowPrefixes(row.getParentRow()!, inverseDepth + 1);
    const thisPrefix = inverseDepth == 0
        ? (isLast ? 'last-child' : 'intermediate-child')
        : (isLast ? 'blank' : 'continue');

    return [...parentPrefixes, <div className={css.treePrefixSymbol} data-symbol-type={thisPrefix}></div>];
}

function ChannelNameCell({ row }: { row: Row<DashboardRowModel> }) {
    let prefix = getRowPrefixes(row);

    return <div className={css.channelNameCell}>
        <div className={css.treePrefix}>
            {prefix}
            {row.getCanExpand() && <button
                type="button"
                className={`${css.expanderButton} ${row.getIsExpanded() ? css.expanded : ''}`}
                onClick={row.getToggleExpandedHandler()}
                aria-label={row.getIsExpanded() ? "Collapse" : "Expand"}>
                {row.getIsExpanded() ? '-' : '+'}
            </button>}
        </div>
        <div className={css.channelNameText}>
            {row.original.name}
        </div>
    </div>;
}

function iconForStatus(state: ChannelStateOrMixed) {
    switch (state) {
        case 'Deploying':
        case 'Undeploying':
        case 'Starting':
        case 'Stopping':
        case 'Pausing':
        case 'Syncing':
        case 'Mixed':
            return '🟠';

        case 'Started':
            return '🟢';

        case 'Stopped':
            return '🔴';

        case 'Paused':
            return '🟡';

        default:
            return '⚫';
    }
}

function StatusCell({ row }: { row: Row<DashboardRowModel> }) {
    return <>
        <span style={{ fontSize: "5pt", verticalAlign: "3px", paddingRight: "2px" }}>{iconForStatus(row.original.state)}</span> {row.original.state}
    </>;
}

function RevisionCountCell({ row }: { row: Row<DashboardRowModel> }) {
    if (!("deployedRevisionDelta" in row.original)) return null;
    return <>
        {row.original.deployedRevisionDelta > 0 ? `+${row.original.deployedRevisionDelta}` : '--'}
    </>;
}

const DashboardColumns: ColumnDef<DashboardRowModel>[] = [
    { header: 'Status', accessorKey: 'state', cell: StatusCell },
    { header: 'Name', accessorKey: 'name', cell: ChannelNameCell, size: 500 },
    { header: 'Rev Δ', accessorKey: 'deployedRevisionDelta', cell: RevisionCountCell },
    {
        header: 'Last Deployed',
        accessorKey: 'deployedDate',
        cell: info => info.getValue() ? new Date(info.getValue() as string).toLocaleString() : '',
    },
    { header: 'Received', accessorKey: 'statistics.RECEIVED' },
    { header: 'Filtered', accessorKey: 'statistics.FILTERED' },
    { header: 'Queued', accessorKey: 'statistics.QUEUED' },
    { header: 'Sent', accessorKey: 'statistics.SENT' },
    { header: 'Errored', accessorKey: 'statistics.ERROR' },
];

export function DashboardList() {
    const sess = useSession();
    const [prefs, setPrefs] = usePreferences<FilterPreferences>(DASHBOARD_PREFS_KEY, () => ({
        textFilter: '',
        useGroups: true,
        statsMode: 'Current',
        tagDisplayMode: 'Icons',
    }));

    const refreshIntervalSeconds = typeof sess.prefs.intervalTime === "number" ? sess.prefs.intervalTime : 10;
    const { data: dashboard } = useQuery({
        queryKey: [CHANNEL_LIST_QUERY_KEY, prefs.textFilter, prefs.useGroups, prefs.statsMode, refreshIntervalSeconds],
        queryFn: () => getDashboardData(prefs),
        refetchInterval: refreshIntervalSeconds * 1000,
    });

    const toggleTagDisplayMode = (button: TagDisplayMode) => {
        if (prefs.tagDisplayMode === button) {
            setPrefs({ tagDisplayMode: 'None' });
        } else {
            setPrefs({ tagDisplayMode: button });
        }
    };

    const rows: DashboardRowModel[] = dashboard?.groups ?? [];
    const table = useReactTable({
        data: rows,
        columns: DashboardColumns,
        getCoreRowModel: getCoreRowModel(),
        getExpandedRowModel: getExpandedRowModel(),
        getSortedRowModel: getSortedRowModel(),
        getSubRows: (row) => "children" in row ? row.children : undefined,
    });

    return <div className={`card p-2 ${css.dashboardListCard}`}>
        <div className={css.dashboardList}>
            <table className={`table table-sm table-striped ${css.dashboardTable}`}>
                <thead>
                    {table.getHeaderGroups().map(headerGroup => (
                        <tr key={headerGroup.id}>
                            {headerGroup.headers.map(header => (
                                <th key={header.id} style={{ width: header.getSize() ? `${header.getSize()}px` : undefined }}
                                    onClick={header.column.getToggleSortingHandler()}>
                                    {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                                    {{ asc: ' ▲', desc: ' ▼' }[header.column.getIsSorted() as string] ?? null}
                                </th>
                            ))}
                        </tr>
                    ))}
                </thead>
                <tbody>
                    {table.getRowModel().rows.map(row => <tr key={row.id}>
                        {row.getVisibleCells().map(cell => (
                            <td key={cell.id} data-column={cell.column.id}>
                                {flexRender(cell.column.columnDef.cell, cell.getContext())}
                            </td>
                        ))}
                    </tr>)}
                </tbody>
            </table>
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

            {dashboard && <div className={css.dashboardSummary}>
                {dashboard.groups.length.toLocaleString()} Groups, {dashboard.deployedChannelCount.toLocaleString()} Deployed Channels
            </div>}

            <div className={css.statsModeSelector}>
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