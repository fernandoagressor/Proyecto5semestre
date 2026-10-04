export interface LoginRequest {
  identificador: string;
  password: string;
}

export interface ChangePasswordRequest {
  passwordActual: string;
  passwordNueva: string;
  confirmacionPassword: string;
}

export interface AuthenticatedUser {
  id: number;
  empresaId: number | null;
  nombreUsuario: string;
  correo: string;
  roles: string[];
  permisos: string[];
  requiereCambioPassword: boolean;
  mfaHabilitado: boolean;
}

export interface TokenResponse {
  tipo: string;
  accessToken: string;
  refreshToken: string;
  expiraEnSegundos: number;
  usuario: AuthenticatedUser;
}

export interface SessionData {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: number;
  user: AuthenticatedUser;
}
