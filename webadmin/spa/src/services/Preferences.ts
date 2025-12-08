import { useState } from "react";
import { useCallback } from "react";

function getPrefsRaw<T>(key: string): Partial<T> {
    try {
        const stored = localStorage.getItem(key);
        if (stored) {
            let parsed = JSON.parse(stored);
            if (typeof parsed === "object" && parsed !== null) {
                return parsed as Partial<T>;
            }
        }
    } catch {
        // This is all best-effort; ignore errors
    }
    return {};
}

export function usePreferences<T>(
    key: string,
    getDefaultPrefs: () => T
): [T, (newPrefs: Partial<T>) => void] {
    const [prefs, setPrefsRaw] = useState<T>(() => {
        return { ...getDefaultPrefs(), ...getPrefsRaw<T>(key) };
    });

    const setPrefs = useCallback(
        (newPrefs: Partial<T>) => {
            setPrefsRaw(s => {
                const newValue = { ...getPrefsRaw<T>(key), ...newPrefs };
                localStorage.setItem(key, JSON.stringify(newValue));
                return { ...s, ...newPrefs };
            });
        },
        [key]
    );

    return [prefs, setPrefs];
}
