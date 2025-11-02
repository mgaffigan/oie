import { } from "react";
import { useQuery } from "@tanstack/react-query";
import { Client } from "../../services/Services";

export const CHANNEL_LIST_QUERY_KEY = 'channelList';

export function ChannelList() {
    const { data: channels } = useQuery({
        queryKey: [CHANNEL_LIST_QUERY_KEY],
        queryFn: async () => {
            const { data, response } = await Client.GET("/channels");
            if (!data) throw new Error(`Failed to fetch channels: ${response.status} ${response.statusText}`);
            return data;
        }
    });

    if (!channels) {
        return <span>Loading...</span>;
    }

    return <div className="card p-2" style={{ height: '100%' }}>
        <ul>
            {channels?.map(c => (<li key={c.id}>{c.name} (ID: {c.id})</li>))}
        </ul>
    </div>;
}
