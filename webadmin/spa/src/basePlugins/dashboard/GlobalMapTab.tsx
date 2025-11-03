import type { OiePluginContext } from "oieshell-common";
import { useState } from "react";
import { usePeriodicAsyncEffect } from "../../services/ReactUtilities";
import css from "./GlobalMapTab.module.scss";

export function GlobalMapTab(props: { context: OiePluginContext }) {
    const client = props.context.client;
    const [logs, setLogs] = useState<Record<string, string>>({});

    usePeriodicAsyncEffect(async () => {
        const { data } = await client.GET("/extensions/globalmapviewer/maps/global");
        if (!data) {
            throw new Error("Could not retrieve global map data");
        }
        setLogs(data);
    }, (e) => { console.error(e); }, 30 * 1000, [client]);

    return <div className={css.globalMapTab}>
        <table className="table table-sm table-striped">
            <thead>
                <tr>
                    <th scope="col">Key</th>
                    <th scope="col">Value</th>
                </tr>
            </thead>
            <tbody>
                {Object.entries(logs).map(([key, value]) => (
                    <tr key={key}>
                        <td>{key}</td>
                        <td>{value}</td>
                    </tr>
                ))}
            </tbody>
        </table>
    </div>;
}
