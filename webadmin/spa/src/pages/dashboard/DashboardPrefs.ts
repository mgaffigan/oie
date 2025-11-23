export const DASHBOARD_PREFS_KEY = "dashboardListPrefs";

export type TagDisplayMode = 'Names' | 'Icons' | 'None';
export type StatisticsDisplayMode = 'Current' | 'Lifetime';

export interface FilterPreferences {
    textFilter: string;
    useGroups: boolean;
    statsMode: StatisticsDisplayMode;
    tagDisplayMode: TagDisplayMode;
}
