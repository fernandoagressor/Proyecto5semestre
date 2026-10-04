import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AppIconComponent } from '../../shared/components/app-icon.component';

interface ModuleDefinition {
  eyebrow: string;
  title: string;
  description: string;
  icon: string;
  workflows: string[];
  integrations: string[];
}

const MODULES: Record<string, ModuleDefinition> = {
  inventario: { eyebrow: 'PROYECTOS E INVENTARIO', title: 'Control de inventario', description: 'Gestiona la jerarquía Proyecto → Etapa → Manzana → Lote con disponibilidad e historial.', icon: 'inventory', workflows: ['Etapas y manzanas', 'Catálogo de lotes', 'Estados y disponibilidad', 'Historial de precios'], integrations: ['20 endpoints protegidos', 'Bloqueo transaccional', 'Aislamiento por proyecto'] },
  crm: { eyebrow: 'CRM COMERCIAL', title: 'Embudo y seguimiento', description: 'Centraliza prospectos, oportunidades, agenda, actividades y simulaciones financieras.', icon: 'crm', workflows: ['Prospectos', 'Oportunidades', 'Agenda comercial', 'Simulaciones'], integrations: ['30 endpoints protegidos', 'Agenda sin cruces', 'Historial de etapas'] },
  ventas: { eyebrow: 'RESERVAS Y CONTRATOS', title: 'Cierre seguro de ventas', description: 'Orquesta reservas, pagos iniciales, contratos, participantes, documentos y firmas.', icon: 'sales', workflows: ['Reservas', 'Validación de pago', 'Contratos versionados', 'Firmas y evidencias'], integrations: ['18 endpoints protegidos', 'Concurrencia pesimista', 'Venta tras firma válida'] },
  cartera: { eyebrow: 'CARTERA Y PAGOS', title: 'Gestión financiera', description: 'Controla planes, cuotas, recaudos, mora, conciliaciones y refinanciaciones.', icon: 'wallet', workflows: ['Planes y cuotas', 'Pagos y reversiones', 'Mora y cobro', 'Conciliación bancaria'], integrations: ['29 endpoints protegidos', 'Aplicación transaccional', 'Cierres reproducibles'] },
  servicio: { eyebrow: 'SERVICIO AL CLIENTE', title: 'Experiencia del cliente', description: 'Atiende PQRS, citas, documentos, comunicaciones, notificaciones y soporte.', icon: 'service', workflows: ['PQRS y conversaciones', 'Agenda de servicio', 'Documentos', 'Tickets de soporte'], integrations: ['49 endpoints protegidos', 'Control de SLA', 'Mensajes privados'] },
  gerencia: { eyebrow: 'GERENCIA Y CONTROL', title: 'Decisiones con trazabilidad', description: 'Consulta metas, comisiones, tareas, rentabilidad, reportes y alertas.', icon: 'management', workflows: ['Metas y comisiones', 'Tareas', 'Presupuestos y costos', 'Reportes'], integrations: ['52 endpoints protegidos', 'Cálculos transaccionales', 'Separación de funciones'] },
  auditoria: { eyebrow: 'AUDITORÍA', title: 'Control y evidencia', description: 'Supervisa eventos, exportaciones y alertas de anomalía sin alterar la bitácora.', icon: 'audit', workflows: ['Eventos auditados', 'Detalle de cambios', 'Alertas', 'Exportaciones'], integrations: ['Bitácora inmutable', 'Filtros multiempresa', 'Acceso restringido'] },
};

@Component({
  selector: 'app-module-workspace',
  imports: [RouterLink, AppIconComponent],
  templateUrl: './module-workspace.page.html',
  styleUrl: './module-workspace.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ModuleWorkspacePage {
  private readonly route = inject(ActivatedRoute);
  readonly definition = computed(() => MODULES[this.route.snapshot.routeConfig?.path ?? 'inventario']);
}
