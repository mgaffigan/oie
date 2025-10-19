import React, { useMemo } from "react";
import { MenuBar } from "./MenuBar";
import css from "./StandardLayout.module.scss";
import { useSession } from "../services/Session";

function awtColorToCssColor(awtColorXml?: string): string | null {
    if (!awtColorXml) {
        return null;
    }

    const doc = new DOMParser().parseFromString(awtColorXml, 'application/xml');
    const red = doc.getElementsByTagName('red')[0]?.textContent;
    if (!red) {
        return null;
    }
    const green = doc.getElementsByTagName('green')[0]?.textContent || '0';
    const blue = doc.getElementsByTagName('blue')[0]?.textContent || '0';
    return `rgb(${red}, ${green}, ${blue})`;
}

function useBackgroundColor(): string {
    const sess = useSession();
    return useMemo(() => {
        const defBg = sess.serverSettings.defaultAdministratorBackgroundColor;
        return awtColorToCssColor(sess.prefs['backgroundColor'])
            ?? (defBg && `rgb(${defBg.red}, ${defBg.green}, ${defBg.blue})`)
            ?? '#9EB1C9';
    }, [sess]);
}

export function StandardLayout(props: {
    title: string;
    commands?: React.ReactNode;
    children: React.ReactNode;
}) {
    let bgCssColor = useBackgroundColor();

    return <div className={css.standardLayout} style={{ backgroundColor: bgCssColor }}>
        <MenuBar commands={props.commands} />
        <main className={css.clientArea}>
            <div className={css.titleBox}>
                <h1 className={css.pageTitle}>{props.title}</h1>
            </div>
            <div className={css.clientContent}>
                {props.children}
            </div>
        </main>
    </div>;
}
