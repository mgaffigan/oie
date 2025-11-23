import { useRef } from "react";
import { useQuery } from "@tanstack/react-query";
import { useReactTable, getCoreRowModel, getExpandedRowModel, flexRender, type ColumnDef } from "@tanstack/react-table";
import { useVirtualizer } from "@tanstack/react-virtual";
import { getDashboardData, type DashboardRowModel } from "./DashboardData";
import { type FilterPreferences, type TagDisplayMode, DASHBOARD_PREFS_KEY } from "./DashboardPrefs";
import css from "./DashboardList.module.scss";
import styleIcon from '../../assets/icons/style.png';
import tagIcon from '../../assets/icons/tag_blue.png';
import serverDatabaseIcon from '../../assets/icons/server_database.png';
import serverIcon from '../../assets/icons/server.png';
import { useLocalStoragePreferences } from "../../services/LocalStoragePreferences";
import { useSession } from "../../services/Session";

export const CHANNEL_LIST_QUERY_KEY = 'channelList';

const DashboardColumns: ColumnDef<DashboardRowModel>[] = [
    { header: 'Status', accessorKey: 'state' },
    {
        id: 'expander',
        header: '',
        cell: ({ row }) => {
            if (!row.getCanExpand()) return null;
            return (
                <button
                    type="button"
                    className="btn btn-sm btn-link"
                    onClick={row.getToggleExpandedHandler()}
                    aria-label={row.getIsExpanded() ? "Collapse" : "Expand"}>
                    {row.getIsExpanded() ? '▼' : '▶'}
                </button>
            );
        },
        size: 32,
    },
    { header: 'Name', accessorKey: 'name' },
    { header: 'Rev Δ', accessorKey: ';' },
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
    const [prefs, setPrefs] = useLocalStoragePreferences<FilterPreferences>(DASHBOARD_PREFS_KEY, () => ({
        textFilter: '',
        useGroups: true,
        statsMode: 'Current',
        tagDisplayMode: 'Icons',
    }));

    const refreshIntervalSeconds = typeof sess.prefs.intervalTime === "number" ? sess.prefs.intervalTime : 10;
    const { data: dashboard } = useQuery({
        queryKey: [CHANNEL_LIST_QUERY_KEY, prefs.textFilter, prefs.statsMode, refreshIntervalSeconds],
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
        getSubRows: (row) => row.children,
    });

    const parentRef = useRef<HTMLDivElement>(null);
    const rowVirtualizer = useVirtualizer({
        count: table.getRowModel().rows.length,
        getScrollElement: () => parentRef.current,
        estimateSize: () => 28,
        overscan: 10,
    });
    const virtualRows = rowVirtualizer.getVirtualItems();

    return <div className={`card p-2 ${css.dashboardListCard}`}>
        <div className={css.dashboardList} ref={parentRef}>
            {!dashboard ? (
                <span>Loading...</span>
            ) : (
                <table className="table table-sm table-striped">
                    <thead>
                        {table.getHeaderGroups().map(headerGroup => (
                            <tr key={headerGroup.id}>
                                {headerGroup.headers.map(header => (
                                    <th key={header.id} style={{ width: header.getSize() ? `${header.getSize()}px` : undefined }}>
                                        {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                                    </th>
                                ))}
                            </tr>
                        ))}
                    </thead>
                    <tbody>
                        {virtualRows.length === 0 ? (
                            <>
                                {table.getRowModel().rows.map(row => (
                                    <tr key={row.id}>
                                        {row.getVisibleCells().map(cell => (
                                            <td key={cell.id} style={cell.column.id === 'expander' ? { paddingLeft: `${row.depth * 16}px` } : undefined}>
                                                {flexRender(cell.column.columnDef.cell, cell.getContext())}
                                            </td>
                                        ))}
                                    </tr>
                                ))}
                            </>
                        ) : (
                            <>
                                <tr>
                                    <td colSpan={table.getVisibleFlatColumns().length} style={{ height: virtualRows[0].start }} />
                                </tr>
                                {virtualRows.map(virtualRow => {
                                    const row = table.getRowModel().rows[virtualRow.index];
                                    return (
                                        <tr key={row.id} style={{ height: virtualRow.size }}>
                                            {row.getVisibleCells().map(cell => (
                                                <td key={cell.id} style={cell.column.id === 'expander' ? { paddingLeft: `${row.depth * 16}px` } : undefined}>
                                                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                                                </td>
                                            ))}
                                        </tr>
                                    );
                                })}
                                <tr>
                                    <td colSpan={table.getVisibleFlatColumns().length} style={{ height: rowVirtualizer.getTotalSize() - virtualRows[virtualRows.length - 1].end }} />
                                </tr>
                            </>
                        )}
                    </tbody>
                </table>
            )}
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

            {dashboard && <div>
                {dashboard.groups.length.toLocaleString()} Groups, {dashboard.deployedChannelCount.toLocaleString()} Deployed Channels
            </div>}

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