import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { CustomerLotFlowPayload, CustomerLotFlowResult } from '../../core/models/customer-lot-flow.models';
import { Project } from '../../core/models/empresa.models';
import { Block, Lot, ProjectStage } from '../../core/models/inventory.models';
import { CustomerLotFlowService } from '../../core/services/customer-lot-flow.service';
import { InventoryService } from '../../core/services/inventory.service';
import { ProjectService } from '../../core/services/project.service';
import { CustomerLotFlowPage } from './customer-lot-flow.page';

describe('CustomerLotFlowPage', () => {
  it('keeps the selected project, structure and customer name synchronized with the forms', () => {
    configureProviders();
    const component = TestBed.runInInjectionContext(() => new CustomerLotFlowPage());

    expect(component.selectedProject()).toBeNull();
    expect(component.selectedStage()).toBeNull();
    expect(component.selectedBlock()).toBeNull();
    expect(component.customerName()).toBe('Nuevo cliente');

    component.stages.set([stage]);
    component.blocks.set([block]);
    component.lotFilterForm.patchValue({ projectId: 7, stageId: 3, blockId: 5 });
    component.customerForm.patchValue({ nombres: 'Ana', apellidos: 'Pérez' });

    expect(component.selectedProject()?.nombre).toBe('Proyecto central');
    expect(component.selectedStage()?.nombre).toBe('Primera etapa');
    expect(component.selectedBlock()?.nombre).toBe('Manzana A');
    expect(component.customerName()).toBe('Ana Pérez');
  });

  it('explains why confirmation cannot continue when the selected project is missing', () => {
    configureProviders();
    const component = TestBed.runInInjectionContext(() => new CustomerLotFlowPage());
    component.selectedLot.set(lot);
    component.step.set(4);

    component.confirm();

    expect(component.step()).toBe(2);
    expect(component.error()).toBe('Selecciona un proyecto válido antes de confirmar la reserva.');
  });

  it('creates the reservation from the real confirmation button after changing the project', async () => {
    let submittedPayload: CustomerLotFlowPayload | null = null;
    const result = {
      cliente: { nombreCompleto: 'Ana Pérez', codigoCliente: 'CLI-001' },
      reserva: { codigo: 'RSV-001', estado: 'PENDIENTE_PAGO' },
      oportunidad: {},
    } as unknown as CustomerLotFlowResult;

    await TestBed.configureTestingModule({
      imports: [CustomerLotFlowPage],
      providers: [
        provideRouter([]),
        ...pageProviders({
          create: (payload: CustomerLotFlowPayload) => {
            submittedPayload = payload;
            return of(result);
          },
        }),
      ],
    }).compileComponents();

    const fixture: ComponentFixture<CustomerLotFlowPage> = TestBed.createComponent(CustomerLotFlowPage);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.selectedProject()).toBeNull();

    component.customerForm.patchValue({
      numeroDocumento: '1002245536',
      nombres: 'Ana',
      apellidos: 'Pérez',
      telefono: '30023303',
    });
    component.lotFilterForm.patchValue({ projectId: 7 });
    component.selectedLot.set(lot);
    component.reservationForm.patchValue({ valorReserva: 500000 });
    component.step.set(4);
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector(
      '[data-testid="confirm-customer-lot"]',
    ) as HTMLButtonElement;

    expect(button).toBeTruthy();
    expect(button.type).toBe('button');

    button.click();

    expect(submittedPayload).toEqual(
      expect.objectContaining({ proyectoId: 7, loteId: 11, valorReserva: 500000 }),
    );
    expect(component.success()).toBe(result);
  });
});

function configureProviders(): void {
  TestBed.configureTestingModule({ providers: pageProviders() });
}

function pageProviders(flowService: object = { create: () => of({} as CustomerLotFlowResult) }) {
  return [
    FormBuilder,
    { provide: ProjectService, useValue: { list: () => of(page([project])) } },
    {
      provide: InventoryService,
      useValue: {
        stages: () => of([stage]),
        blocks: () => of([block]),
        listLots: () => of(page([lot])),
      },
    },
    { provide: CustomerLotFlowService, useValue: flowService },
  ];
}

function page<T>(content: T[]) {
  return {
    contenido: content,
    pagina: 0,
    tamano: content.length,
    totalElementos: content.length,
    totalPaginas: content.length ? 1 : 0,
    primera: true,
    ultima: true,
  };
}

const project: Project = {
  id: 7,
  codigo: 'PR-007',
  nombre: 'Proyecto central',
  descripcion: null,
  ciudad: 'Cartagena',
  departamento: 'Bolívar',
  direccion: null,
  fechaInicio: null,
  fechaFinEstimada: null,
  estado: 'ACTIVO',
  porcentajeAvance: 0,
  maxLotes: null,
  imagenPrincipalUrl: null,
  gerenteId: null,
  creadoEn: '2026-08-20T10:00:00',
  actualizadoEn: '2026-08-20T10:00:00',
};

const stage: ProjectStage = {
  id: 3,
  proyectoId: 7,
  codigo: 'ET-001',
  nombre: 'Primera etapa',
  secuencia: 1,
  fechaInicio: null,
  fechaFin: null,
  estado: 'ACTIVA',
};

const block: Block = {
  id: 5,
  proyectoId: 7,
  etapaId: 3,
  codigo: 'MZ-A',
  nombre: 'Manzana A',
  secuencia: 1,
  estado: 'ACTIVA',
};

const lot: Lot = {
  id: 11,
  proyectoId: 7,
  etapaId: 3,
  manzanaId: 5,
  codigo: 'LT-001',
  numero: '001',
  areaM2: 250,
  frenteM: 10,
  fondoM: 25,
  valorLista: 50000000,
  estado: 'DISPONIBLE',
  disponible: true,
  ubicacionReferencia: null,
  descripcion: null,
  version: 0,
  creadoEn: '2026-08-20T10:00:00',
  actualizadoEn: '2026-08-20T10:00:00',
};
