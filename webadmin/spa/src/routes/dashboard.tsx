import { DataTable } from '@/components/data-table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import type { ColumnDef } from '@tanstack/react-table';
import React from 'react';

export const Route = createFileRoute('/dashboard')({
    component: DashboardPage,
});

type MirthChannel = {
    id: string;
    name: string;
    revision: number;
    properties?: { initialState?: 'STARTED' | 'PAUSED' | 'STOPPED' | string };
    sourceConnector?: {
        transportName?: string;
        mode?: string;
        enabled?: boolean;
    };
    destinationConnectors?: unknown;
    description?: string | null;
    ['@version']?: string;
};

type ChannelsResponse = {
    list: {
        channel: MirthChannel | MirthChannel[];
    };
};

async function fetchChannels(): Promise<MirthChannel[]> {
    const r = await fetch('/api/channels', {
        headers: {
            accept: 'application/json',
            'X-Requested-With': 'OIEUI',
        },
        credentials: 'include',
    });
    if (!r.ok) throw new Error(`Failed to load channels: ${r.status}`);
    const json = (await r.json()) as ChannelsResponse;
    const raw = json?.list?.channel;
    const arr = Array.isArray(raw) ? raw : raw ? [raw] : [];
    // defensive: only pick fields we actually use
    return arr.map((c) => ({
        id: c.id,
        name: c.name,
        revision: c.revision,
        properties: c.properties,
        sourceConnector: c.sourceConnector,
        description: c.description,
        ['@version']: c['@version'],
    }));
}

function StateBadge({ c }: { c: MirthChannel }) {
    const initial = c.properties?.initialState ?? 'UNKNOWN';
    const enabled = c.sourceConnector?.enabled;
    const label =
        enabled === false
            ? 'DISABLED'
            : initial === 'STARTED'
              ? 'STARTED'
              : initial;
    const variant =
        label === 'STARTED'
            ? 'success'
            : label === 'DISABLED'
              ? 'secondary'
              : label === 'PAUSED'
                ? 'warning'
                : 'outline';
    return <Badge variant={variant as any}>{label}</Badge>;
}

function DashboardPage() {
    const queryClient = useQueryClient();
    const {
        data = [],
        isLoading,
        isError,
        refetch,
    } = useQuery({
        queryKey: ['channels'],
        queryFn: fetchChannels,
        staleTime: 10_000,
    });

    const columns = React.useMemo<ColumnDef<MirthChannel>[]>(
        () => [
            {
                header: 'Name',
                accessorKey: 'name',
                cell: (info) => (
                    <div className="font-medium">
                        {String(info.getValue() ?? '')}
                    </div>
                ),
            },
            {
                header: 'Status',
                id: 'status',
                enableSorting: false,
                cell: ({ row }) => <StateBadge c={row.original} />,
            },
            {
                header: 'Revision',
                accessorKey: 'revision',
                size: 80,
            },
            {
                header: 'ID',
                accessorKey: 'id',
                cell: (info) => (
                    <code className="text-xs text-muted-foreground">
                        {String(info.getValue() ?? '')}
                    </code>
                ),
            },
            {
                header: 'Source',
                id: 'source',
                accessorFn: (r) => r.sourceConnector?.transportName ?? '—',
            },
        ],
        [],
    );

    return (
        <div className="p-4 md:p-6 space-y-4">
            <div className="flex items-center justify-between gap-2">
                <div>
                    <h1 className="text-xl md:text-2xl font-semibold">
                        Channels
                    </h1>
                    <p className="text-sm text-muted-foreground">
                        {isLoading ? 'Loading…' : `${data.length} total`}
                    </p>
                </div>
                <div className="flex items-center gap-2">
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={() => refetch()}
                        disabled={isLoading}
                    >
                        Refresh
                    </Button>
                </div>
            </div>

            <DataTable
                data={data}
                columns={columns}
                isLoading={isLoading}
                isError={isError}
                initialColumnVisibility={{
                    id: true,
                    name: true,
                    revision: false,
                    source: true,
                    status: true,
                }}
                placeholder="Filter channels…"
            />
        </div>
    );
}
