import { TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';

import { Lot, UpdateLotPayload } from '../../core/models/inventory.models';
import { AuthService } from '../../core/services/auth.service';
import { InventoryService } from '../../core/services/inventory.service';
import { ProjectService } from '../../core/services/project.service';
import { InventoryPage } from './inventory.page';

describe('InventoryPage', () => {
  it('calculates the lot area when its frontage and depth are entered', () => {
    const component = createInventoryPage();

    component.openCreateLot();
    component.lotForm.patchValue({ frenteM: 8.25, fondoM: 12.5 });

    expect(component.lotForm.controls.areaM2.value).toBe(103.13);
  });

  it('updates an existing lot using the physical-data endpoint', () => {
    let updatedPayload: UpdateLotPayload | null = null;
    let updatedLotId: number | null = null;
    const component = createInventoryPage({
      updateLot: (_projectId: number, lotId: number, payload: UpdateLotPayload) => {
        updatedLotId = lotId;
        updatedPayload = payload;
        return of({ ...lot, ...payload });
      },
    });
    component.selectedProjectId.set(7);

    component.openEditLot(lot);
    component.lotForm.patchValue({ codigo: 'LT-002', numero: '002', frenteM: 9.5, fondoM: 14 });
    component.submitLot();

    expect(updatedLotId).toBe(11);
    expect(updatedPayload).toEqual(expect.objectContaining({
      codigo: 'LT-002',
      numero: '002',
      areaM2: 133,
      frenteM: 9.5,
      fondoM: 14,
    }));
    expect(component.feedback()).toBe('Lote actualizado correctamente.');
  });

  it('does not allow physical changes to reserved lots', () => {
    const component = createInventoryPage();

    component.openEditLot({ ...lot, estado: 'RESERVADO', disponible: false });

    expect(component.editingLot()).toBeNull();
    expect(component.feedback()).toContain('reservados o vendidos');
  });
});

function createInventoryPage(overrides: Record<string, unknown> = {}): InventoryPage {
  const emptyPage = {
    contenido: [],
    pagina: 0,
    tamano: 20,
    totalElementos: 0,
    totalPaginas: 0,
    primera: true,
    ultima: true,
  };
  const inventory = {
    blocks: () => of([]),
    stages: () => of([]),
    summary: () => of({ total: 0, disponibles: 0, reservados: 0, vendidos: 0, bloqueados: 0, valorDisponible: 0 }),
    listLots: () => of(emptyPage),
    ...overrides,
  };

  TestBed.configureTestingModule({
    providers: [
      FormBuilder,
      { provide: AuthService, useValue: { hasAuthority: () => true } },
      { provide: InventoryService, useValue: inventory },
      { provide: ProjectService, useValue: { list: () => of(emptyPage) } },
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
      { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
    ],
  });

  return TestBed.runInInjectionContext(() => new InventoryPage());
}

const lot: Lot = {
  id: 11,
  proyectoId: 7,
  etapaId: 3,
  manzanaId: 5,
  codigo: 'LT-001',
  numero: '001',
  areaM2: 120,
  frenteM: 8,
  fondoM: 15,
  valorLista: 50000000,
  estado: 'DISPONIBLE',
  disponible: true,
  ubicacionReferencia: 'Esquina norte',
  descripcion: 'Lote inicial',
  version: 0,
  creadoEn: '2026-08-20T10:00:00',
  actualizadoEn: '2026-08-20T10:00:00',
};
