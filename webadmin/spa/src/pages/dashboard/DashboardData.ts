import type { components } from "oieapi-types/index.d.ts";
import { Client } from "../../services/Services";
import type { FilterPreferences } from "./DashboardPrefs";

type DashboardStatusDTO = components["schemas"]["DashboardStatus"];
type ChannelTagsDTO = components["schemas"]["ChannelTag"];
type ChannelGroupDTO = components["schemas"]["ChannelGroup"];
type ChannelState = DashboardStatusDTO["state"];
type ChannelStatusType = DashboardStatusDTO["statusType"];

const INT32_MAX = 2147483647;
const DEFAULT_GROUP_ID = "Default Group";
const DEFAULT_GROUP_NAME = "[Default Group]";

export type StatisticsMap = Record<string, number>;

export interface DashboardModel {
    groups: DashboardChannelGroupModel[];
    tags: ChannelTagsDTO[];
    deployedChannelCount: number;
}

export interface DashboardChannelGroupModel {
    groupId: string;
    name: string;
    description: string;
    children: DashboardChannelModel[];
    state: ChannelStateOrMixed;
    statistics: StatisticsMap;
}

export interface DashboardChannelModel {
    channelId: string;
    name: string;

    codeTemplatesChanged: boolean;
    deployedDate: string;
    deployedRevisionDelta: number;

    waitForPrevious: boolean;
    queueEnabled: boolean;
    state: ChannelState;
    statusType: ChannelStatusType;

    statistics: StatisticsMap;

    children: DashboardConnectorModel[];

    tags: ChannelTagsDTO[];
}

export interface DashboardConnectorModel {
    destinationId: number;
    name: string;

    waitForPrevious: boolean;
    queueEnabled: boolean;
    state: ChannelState;
    statusType: ChannelStatusType;

    statistics: StatisticsMap;
}

export type DashboardRowModel = (DashboardChannelGroupModel | DashboardChannelModel | DashboardConnectorModel) & { children?: DashboardRowModel[] };

function aggregateStatistics(statsList: StatisticsMap[]): StatisticsMap {
    return statsList.reduce((aggregate, stats) => {
        for (const [key, value] of Object.entries(stats)) {
            aggregate[key] = (aggregate[key] ?? 0) + value;
        }
        return aggregate;
    }, {} as StatisticsMap);
}

export type ChannelStateOrMixed = ChannelState | 'Mixed';
function aggregateChannelStatus(status: ChannelState[]): ChannelStateOrMixed {
    if (status.length === 0) {
        return 'Unknown';
    }
    const firstStatus = status[0];
    for (const s of status) {
        if (s !== firstStatus) {
            return 'Mixed';
        }
    }
    return firstStatus;
}

export async function getDashboardData(prefs: FilterPreferences): Promise<DashboardModel> {
    // Fetch statuses + metadata concurrently
    const [{ data: channels }, { data: groups }, { data: tags }] = await Promise.all([
        Client.GET("/channels/statuses/initial", {
            params: { query: { fetchSize: INT32_MAX, filter: prefs.textFilter } }
        }),
        // TODO: This is silly-buggers.  It includes the full channel definition, which
        // could be unreasonably large and is not necessary for grouping purposes.
        prefs.useGroups ? Client.GET("/channelgroups") : Promise.resolve({ data: [] as ChannelGroupDTO[] }),
        Client.GET("/server/channelTags"),
    ]);

    if (!channels) throw new Error("Failed to fetch initial channel statuses");
    if (!groups) throw new Error("Failed to fetch channel groups");
    if (!tags) throw new Error("Failed to fetch tags");

    // Figure out where channels should go
    const channelIdGroupMap = new Map<string, string>();
    const groupMap = new Map<string, ChannelGroupDTO>();
    for (const group of groups) {
        for (const ch of group.channelIds) {
            channelIdGroupMap.set(ch, group.id);
        }
        groupMap.set(group.id, group);
    }

    // Make the groups
    const channelGroups = new Map<string, DashboardChannelGroupModel>();
    for (const channel of channels.dashboardStatuses) {
        const groupId = channelIdGroupMap.get(channel.channelId!) ?? DEFAULT_GROUP_ID;
        let group = channelGroups.get(groupId);
        if (!group) {
            channelGroups.set(groupId, group = {
                groupId: groupId,
                name: groupMap.get(groupId)?.name ?? DEFAULT_GROUP_NAME,
                description: groupMap.get(groupId)?.description ?? '',
                children: [],
                state: 'Unknown',
                statistics: {},
            });
        }

        // Make the channel
        group.children.push({
            channelId: channel.channelId!,
            name: channel.name,
            codeTemplatesChanged: channel.codeTemplatesChanged!,
            deployedDate: channel.deployedDate!,
            deployedRevisionDelta: channel.deployedRevisionDelta!,
            waitForPrevious: channel.waitForPrevious,
            queueEnabled: channel.queueEnabled,
            state: channel.state,
            statusType: channel.statusType,
            statistics: { ...(prefs.statsMode === 'Current' ? channel.statistics : channel.lifetimeStatistics), QUEUED: channel.queued },
            children: channel.childStatuses.map(connector => ({
                destinationId: connector.metaDataId!,
                name: connector.name,
                waitForPrevious: connector.waitForPrevious,
                queueEnabled: connector.queueEnabled,
                state: connector.state,
                statusType: connector.statusType,
                statistics: { ...(prefs.statsMode === 'Current' ? connector.statistics : connector.lifetimeStatistics), QUEUED: connector.queued },
            })),
            tags: tags.filter(t => t.channelIds.includes(channel.channelId)),
        });
    }

    // Aggregate group statistics
    for (const group of channelGroups.values()) {
        group.statistics = aggregateStatistics(group.children.map(c => c.statistics));
        group.state = aggregateChannelStatus(group.children.map(c => c.state));
    }

    return {
        groups: Array.from(channelGroups.values()),
        tags: tags,
        deployedChannelCount: channels.deployedChannelCount,
    };
}