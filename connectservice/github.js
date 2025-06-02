const REPO_URL = process.env.NOTIFICATIONS_URL ?? 'https://api.github.com/repos/OpenIntegrationEngine/engine/releases?per_page=10';

export async function getLatestReleases() {
    const response = await fetch(REPO_URL, {
        headers: {
            'Accept': 'application/vnd.github.html+json',
            'User-Agent': 'OIEConnectService',
        },
    });

    if (!response.ok) {
        throw new Error(`Failed to fetch latest release: ${response.statusText}`);
    }

    const releases = await response.json();
    return releases.map(release => ({
        id: release.id,
        name: release.name,
        body_html: release.body_html,
        published_at: release.published_at,
    }));
}

/*
interface Release {
    id: number;
    name: string;
    body_html: string;
    published_at: string;
}
*/
