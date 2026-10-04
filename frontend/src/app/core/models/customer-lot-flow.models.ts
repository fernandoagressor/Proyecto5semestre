import { Opportunity } from './crm.models';
import { Customer, CustomerPayload, Reservation } from './sales.models';

export interface CustomerLotFlowPayload {
  cliente: CustomerPayload;
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
