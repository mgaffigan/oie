import { CommandButton, CommandGroup } from "../../layout/MenuBar";
import { StandardLayout } from "../../layout/StandardLayout";
import { useQuery } from "@tanstack/react-query";
import refreshIcon from '../../assets/icons/arrow_refresh.png';
import { Client } from "../../services/Services";

export function DashboardPage() {
    const { data: channels, refetch } = useQuery({
        queryKey: ["channels"],
        queryFn: async () => {
            const { data, response } = await Client.GET("/channels");
            if (!data) throw new Error(`Failed to fetch channels: ${response.status} ${response.statusText}`);
            return data;
        }
    });

    if (!channels) {
        return <span>Loading...</span>;
    }

    const commands = <>
        <CommandGroup title="Dashboard Tasks">
            <CommandButton title="Refresh" icon={refreshIcon} onClick={refetch} />
        </CommandGroup>
    </>;

    return <StandardLayout title="Dashboard" commands={commands}>
        <div className="card p-2" style={{ height: '100%' }}>
            <ul>
                {channels?.map(c => (<li key={c.id}>{c.name} (ID: {c.id})</li>))}
            </ul>
        </div>
    </StandardLayout>;
}