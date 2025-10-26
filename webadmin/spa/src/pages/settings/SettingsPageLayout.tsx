import { } from 'react';
import { StandardLayout } from '../../layout/StandardLayout';
import type { PageHookRegistration } from '../../services/PluginRegistry';
import css from './settings.module.scss';
import { NavLink } from 'react-router';

export function SettingsPageLayout(props: { 
    title: string;
    allTabs: Array<PageHookRegistration>;
    commands?: React.ReactNode;
    children: React.ReactNode;
}) {
    return (
        <StandardLayout title={"Settings > " + props.title} commands={props.commands}>
            <div className={css.settingsPage}>
                <div className={css.tabStrip}>
                    {props.allTabs.map(tab => (
                        <NavLink
                            key={tab.hookId}
                            to={`/settings/${tab.hookId}`}
                            className={({ isActive }) => `${css.tab} ${isActive ? css.active : ''}`}
                        >
                            {tab.header}
                        </NavLink>
                    ))}
                </div>
                <div className={css.tabContent}>
                    {props.children}
                </div>
            </div>
        </StandardLayout>
    );
}