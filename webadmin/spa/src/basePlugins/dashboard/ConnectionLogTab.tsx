import type { OiePluginContext } from "oieshell-common";
import { useState } from "react";
import { usePeriodicAsyncEffect } from "../../services/ReactUtilities";
import type { components } from "oieapi-types/index.d.ts";
import css from "./ConnectionLogTab.module.scss";
import playIcon from "../../assets/icons/control_play_blue.png";
import pauseIcon from "../../assets/icons/control_pause_blue.png";
import xIcon from "../../assets/icons/cross.png";

type ConnectionLogItem = components["schemas"]["ConnectionLogItem"];

function iconForStatus(status: string | undefined) {
    switch (status) {
        case "Idle":
            return "🟡";
        case "Reading":
            return "🟢";
        case "Writing":
            return "🟢";
        case "Polling":
            return "🟢";
        case "Receiving":
            return "🟢";
        case "Sending":
            return "🟢";
        case "Waiting for Response":
            return "🟡";
        case "Connected":
            return "🟢";
        case "Connecting":
            return "🟡";
        case "Disconnected":
            return "🔴";
        case "Info":
            return "🔵";
        case "Failure":
        default:
            return "⚫";
    }
}

export function ConnectionLogTab(props: { context: OiePluginContext }) {
    const client = props.context.client;
    const [isPaused, setIsPaused] = useState(false);
    const [ignoreBefore, setIgnoreAfter] = useState<number>();
    const [maxLines, setMaxLines] = useState<number>(50);
    const [logs, setLogs] = useState<Array<ConnectionLogItem>>([]);

    usePeriodicAsyncEffect(async () => {
        if (isPaused) return;

        const response = await client.GET("/extensions/dashboardstatus/connectionLogs", {
            params: {
                query: {
                    fetchSize: 50,
                    lastLogId: ignoreBefore,
                }
            }
        });
        setLogs(response.data!);
    }, () => { /* ignore errors */ }, 30 * 1000, [isPaused, client, ignoreBefore]);

    return <div className={css.connectionLogTab}>
        <div className={css.logContent}>
            <table className="table table-sm table-striped">
                <thead>
                    <tr>
                        <th scope="col">Timestamp</th>
                        <th scope="col">Channel</th>
                        <th scope="col">Connector Info</th>
                        <th scope="col">Event</th>
                        <th scope="col">Info</th>
                    </tr>
                </thead>
                <tbody>
                    {logs?.map(log => (
                        <tr key={log.logId}>
                            <td title={log.dateAdded}>{new Date(log.dateAdded!).toLocaleString()}</td>
                            <td title={log.channelId}>{log.channelName}</td>
                            <td>{log.connectorType}</td>
                            <td><span style={{ fontSize: "5pt", verticalAlign: "3px", paddingRight: "2px" }}>{iconForStatus(log.eventState)}</span> {log.eventState}</td>
                            <td>{log.information}</td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
        <div className={css.toolbar}>
            <button className="btn btn-outline" onClick={() => setIsPaused(!isPaused)}
                title={isPaused ? "Resume Log Updates" : "Pause Log Updates"}
                aria-label={isPaused ? "Resume Log Updates" : "Pause Log Updates"}>
                <img src={isPaused ? playIcon : pauseIcon} aria-hidden="true" />
            </button>
            <button className="btn btn-outline" onClick={() => {
                if (logs && logs.length > 0) {
                    setIgnoreAfter(logs[0].logId!);
                }
            }} title="Clear Displayed Logs (only affects this session)" aria-label="Clear Displayed Logs">
                <img src={xIcon} aria-hidden="true" />
            </button>

            <form style={{ marginLeft: 'auto' }} className="d-flex flex-row align-items-center" onSubmit={e => e.preventDefault()}>
                <label htmlFor="server-log-max-lines-input" className="text-nowrap me-2">
                    Log Size:
                </label>
                <input id="server-log-max-lines-input" className="form-control d-inline-block" type="number" min={10} max={1000} step={10} value={maxLines}
                    onChange={e => setMaxLines(parseInt(e.target.value, 10) || 50)} />
            </form>
        </div>
    </div>;
}
