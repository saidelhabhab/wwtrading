import { InjectionToken } from '@angular/core';
import Keycloak from 'keycloak-js';

export const KEYCLOAK = new InjectionToken<Keycloak>('WWTRADING_KEYCLOAK');
