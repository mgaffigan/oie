/** A string with localization to multiple locales */
export interface LocalizedStringTable {
    [locale: string]: string;
};

export type LocalizedString = string | LocalizedStringTable;

/**
 * Get a localized string for a specific locale or locales.
 * @param localized The localized strings object.
 * @param locale The list or single locale in order of preference.  Defaults to browser.languages if not provided.
 * @returns The localized string or an empty string if not found.
 */
export function getLocalizedString(localized: LocalizedString, locale?: string | readonly string[]): string {
    if (typeof localized === 'string') {
        return localized;
    }

    let preferredLocales: readonly string[];
    if (!locale) {
        preferredLocales = navigator.languages;
    } else if (typeof locale === 'string') {
        preferredLocales = [locale];
    } else {
        preferredLocales = locale;
    }

    for (const loc of preferredLocales) {
        // Check for exact match
        if (loc in localized) {
            return localized[loc];
        }

        // Note: this ignores script, so is not ideal for zh, but works for western languages
        // Check for language-only match
        const langOnly = loc.split('-')[0];
        if (langOnly in localized) {
            return localized[langOnly];
        }
    }

    if ('default' in localized) {
        return localized['default'];
    }

    // Fallback to first
    return Object.values(localized)[0] || '';
}
