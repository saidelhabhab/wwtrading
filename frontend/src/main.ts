import { bootstrapApplication } from '@angular/platform-browser';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import Keycloak from 'keycloak-js';

import { AppComponent } from './app/app.component';
import { routes } from './app/app.routes';
import { authInterceptor } from './app/core/auth/auth.interceptor';
import { KEYCLOAK } from './app/core/auth/keycloak.token';
import { loadRuntimeConfig, RUNTIME_CONFIG, type RuntimeConfig } from './app/core/config/runtime-config';

const FALLBACK_RUNTIME_CONFIG: RuntimeConfig = {
  apiBaseUrl: '',
  siteUrl: '',
  googleSiteVerification: '',
  googleAnalyticsMeasurementId: '',
  keycloak: {
    url: '',
    realm: 'wwtrading',
    clientId: 'wwtrading-web'
  }
};

async function bootstrap(): Promise<void> {
  let config: RuntimeConfig = FALLBACK_RUNTIME_CONFIG;
  try {
    config = await loadRuntimeConfig();
  } catch (error) {
    console.warn('WWTRADING runtime configuration unavailable; continuing in public mode', error);
  }

  const keycloak = new Keycloak(config.keycloak);
  const hasKeycloakConfig = Boolean(config.keycloak.url && config.keycloak.realm && config.keycloak.clientId);
  if (hasKeycloakConfig) {
    try {
      await keycloak.init({
        onLoad: 'check-sso',
        pkceMethod: 'S256',
        silentCheckSsoRedirectUri: `${location.origin}/silent-check-sso.html`,
        checkLoginIframe: true
      });
    } catch (error) {
      console.warn('WWTRADING Keycloak init failed; continuing without authenticated session', error);
    }
  }

  await bootstrapApplication(AppComponent, {
    providers: [
      provideAnimationsAsync(),
      provideRouter(routes, withComponentInputBinding()),
      provideHttpClient(withInterceptors([authInterceptor])),
      { provide: KEYCLOAK, useValue: keycloak },
      { provide: RUNTIME_CONFIG, useValue: config }
    ]
  });
}

bootstrap().catch((error) => console.error('WWTRADING bootstrap failed', error));
