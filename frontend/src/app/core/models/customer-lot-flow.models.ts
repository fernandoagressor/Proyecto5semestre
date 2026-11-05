import { Opportunity } from './crm.models';
import { Customer, CustomerPayload, Reservation } from './sales.models';

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
}
