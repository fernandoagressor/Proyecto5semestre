import { Injectable } from '@angular/core';

import { SessionData, TokenResponse } from '../models/auth.models';

const SESSION_KEY = 'nexora_core_session';

@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  read(): SessionData | null {
    if (typeof sessionStorage === 'undefined') return null;

    const raw = sessionStorage.getItem(SESSION_KEY);
    if (!raw) return null;

    try {
      const parsed = JSON.parse(raw) as SessionData;
      if (!parsed.accessToken || !parsed.refreshToken || !parsed.user) {
        this.clear();
        return null;
      }
      return parsed;
    } catch {
      this.clear();
      return null;
    }
  }

  write(response: TokenResponse): SessionData {
    const session: SessionData = {
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      accessTokenExpiresAt: Date.now() + response.expiraEnSegundos * 1000,
      user: response.usuario,
    };
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
    return session;
  }

  clear(): void {
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.removeItem(SESSION_KEY);
    }
  }
}
