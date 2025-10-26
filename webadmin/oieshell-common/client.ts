import type { Client } from 'openapi-fetch';
import type { paths as EngineApi } from 'oieapi-types/index.d.ts';

export type ClientApi = Client<EngineApi, 'application/mirthapi+json'>;
