import type { OiePluginContext } from "oieshell-common";
import { usePeriodicAsyncEffect } from "../../services/ReactUtilities";
import { useState } from "react";
import type { components } from "oieapi-types/index.d.ts";
import css from "./ServerLogTab.module.scss";
import playIcon from "../../assets/icons/control_play_blue.png";
import pauseIcon from "../../assets/icons/control_pause_blue.png";
import xIcon from "../../assets/icons/cross.png";

type ServerLogItem = components["schemas"]["ServerLogItem"];

export function ServerLogTab(props: { context: OiePluginContext }) {
    const client = props.context.client;
    const [isPaused, setIsPaused] = useState(false);
    const [ignoreBefore, setIgnoreAfter] = useState<number>();
    const [maxLines, setMaxLines] = useState<number>(50);
    const [logs, setLogs] = useState<Array<ServerLogItem>>([]);

    usePeriodicAsyncEffect(async () => {
        if (isPaused) return;

        const response = await client.GET("/extensions/serverlog", {
            params: {
                query: {
                    fetchSize: maxLines,
                    lastLogId: ignoreBefore,
                }
            }
        });
        setLogs(response.data!);
    }, () => { /* ignore errors */ }, 30 * 1000, [isPaused, client, ignoreBefore]);

    return <div className={css.serverLogTab}>
        <div className={css.logContent}>
            {logs?.map(log => (
                <div key={log.id}>
                    [<span title={log.date}>{new Date(log.date!).toLocaleString()}</span>] {log.level} ({log.category}:{log.lineNumber}) {log.message}
                </div>
            ))}
        </div>
        <div className={css.toolbar}>
            <button className="btn btn-outline" onClick={() => setIsPaused(!isPaused)}
                title={isPaused ? "Resume Log Updates" : "Pause Log Updates"}
                aria-label={isPaused ? "Resume Log Updates" : "Pause Log Updates"}>
                <img src={isPaused ? playIcon : pauseIcon} aria-hidden="true" />
            </button>
            <button className="btn btn-outline" onClick={() => {
                if (logs && logs.length > 0) {
                    setIgnoreAfter(logs[0].id!);
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
