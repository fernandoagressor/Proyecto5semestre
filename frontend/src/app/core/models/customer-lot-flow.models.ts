import { Opportunity } from './crm.models';
import { Customer, CustomerPayload, PortalAccess, Reservation } from './sales.models';

/** Reservar un lote de una vez: con `clienteId` (ya existe) o con `cliente` (nuevo). */
export interface CustomerLotFlowPayload {
  cliente: CustomerPayload | null;
  clienteId?: number | null;
  proyectoId: number;
  loteId: number;
  fechaVencimiento: string;
  valorReserva: number;
  formaPago: string;
}

export interface CustomerLotFlowResult {
  cliente: Customer;
  oportunidad: Opportunity;
  reserva: Reservation;
  /** Usuario y contraseña temporal del portal creados al reservar (se muestran una sola vez). */
  accesoPortal: PortalAccess | null;
  /** El cliente ya tenía acceso a su portal. */
  portalExistente: boolean;
}
