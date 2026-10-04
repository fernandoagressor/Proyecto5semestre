import { FormBuilder } from '@angular/forms';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { Reservation } from '../../core/models/sales.models';
import { AuthService } from '../../core/services/auth.service';
import { CrmService } from '../../core/services/crm.service';
import { InventoryService } from '../../core/services/inventory.service';
import { ProjectService } from '../../core/services/project.service';
import { SalesService } from '../../core/services/sales.service';
import { SalesPage } from './sales.page';

describe('SalesPage', () => {
  it('updates the open reservation immediately after confirming its payment', () => {
    const pendingReservation = reservation({
      estado: 'PENDIENTE_PAGO',
      estadoPago: 'PENDIENTE',
      comprobanteArchivoId: null,
      pagoConfirmadoEn: null,
      pagoConfirmadoPor: null,
    });
    const activeReservation = reservation({
      estado: 'ACTIVA',
      estadoPago: 'CONFIRMADO',
      comprobanteArchivoId: 91,
      pagoConfirmadoEn: '2026-08-11T10:30:00',
      pagoConfirmadoPor: 7,
    });
    const sales = {
      uploadDocument: () =>
        of({
          id: 91,
          empresaId: 2,
          proyectoId: 1,
          nombreOriginal: 'comprobante.pdf',
          mimeType: 'application/pdf',
          tamanoBytes: 12,
          sha256: 'a'.repeat(64),
          clasificacion: 'CONFIDENCIAL',
          creadoEn: '2026-08-11T10:30:00',
        }),
      confirmReservationPayment: () => of(activeReservation),
      reservations: () =>
        of({
          contenido: [activeReservation],
          pagina: 0,
          tamano: 1,
          totalElementos: 1,
          totalPaginas: 1,
          primera: true,
          ultima: true,
        }),
    };

    TestBed.configureTestingModule({
      providers: [
        FormBuilder,
        { provide: AuthService, useValue: { hasAuthority: () => true } },
        { provide: SalesService, useValue: sales },
        { provide: ProjectService, useValue: {} },
        { provide: CrmService, useValue: {} },
        { provide: InventoryService, useValue: {} },
      ],
    });

    const page = TestBed.runInInjectionContext(() => new SalesPage());
    page.reservations.set([pendingReservation]);
    page.selectedReservation.set(pendingReservation);
    page.selectedFile.set(
      new File(['comprobante'], 'comprobante.pdf', { type: 'application/pdf' }),
    );

    page.confirmPayment();

    expect(page.selectedReservation()?.estado).toBe('ACTIVA');
    expect(page.selectedReservation()?.estadoPago).toBe('CONFIRMADO');
    expect(page.selectedReservation()?.comprobanteArchivoId).toBe(91);
    expect(page.reservations()[0].estado).toBe('ACTIVA');
    expect(page.modal()).toBeNull();
  });

  it('confirms the payment from the real modal button without submitting a form', async () => {
    const pendingReservation = reservation({
      estado: 'PENDIENTE_PAGO',
      estadoPago: 'PENDIENTE',
    });
    const activeReservation = reservation({
      estado: 'ACTIVA',
      estadoPago: 'CONFIRMADO',
      comprobanteArchivoId: 91,
    });
    let uploads = 0;
    let confirmations = 0;
    const sales = {
      uploadDocument: () => {
        uploads += 1;
        return of({
          id: 91,
          empresaId: 2,
          proyectoId: 1,
          nombreOriginal: 'comprobante.pdf',
          mimeType: 'application/pdf',
          tamanoBytes: 12,
          sha256: 'a'.repeat(64),
          clasificacion: 'CONFIDENCIAL',
          creadoEn: '2026-08-11T10:30:00',
        });
      },
      confirmReservationPayment: () => {
        confirmations += 1;
        return of(activeReservation);
      },
      reservations: () => of(page([activeReservation])),
      contracts: () => of(page([])),
      customers: () => of(page([])),
    };

    await TestBed.configureTestingModule({
      imports: [SalesPage],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { hasAuthority: () => true } },
        { provide: SalesService, useValue: sales },
        { provide: ProjectService, useValue: { list: () => of(page([])) } },
        { provide: CrmService, useValue: {} },
        { provide: InventoryService, useValue: {} },
      ],
    }).compileComponents();

    const fixture: ComponentFixture<SalesPage> = TestBed.createComponent(SalesPage);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    component.openPayment(pendingReservation);
    component.selectedFile.set(
      new File(['comprobante'], 'comprobante.pdf', { type: 'application/pdf' }),
    );
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector(
      '[data-testid="confirm-payment"]',
    ) as HTMLButtonElement;
    expect(button).toBeTruthy();
    expect(button.type).toBe('button');
    expect(button.closest('form')).toBeNull();

    button.click();
    fixture.detectChanges();

    expect(uploads).toBe(1);
    expect(confirmations).toBe(1);
    expect(component.selectedReservation()?.estado).toBe('ACTIVA');
    expect(component.modal()).toBeNull();
  });
});

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

function reservation(overrides: Partial<Reservation>): Reservation {
  return {
    id: 15,
    empresaId: 2,
    proyectoId: 1,
    codigo: 'RSV-PRUEBA',
    oportunidadId: 4,
    simulacionId: null,
    clienteId: 3,
    loteId: 8,
    asesorId: 7,
    fechaReserva: '2026-08-11T09:00:00',
    fechaVencimiento: '2026-08-18T09:00:00',
    valorReserva: 500000,
    formaPago: 'TRANSFERENCIA',
    estadoPago: 'PENDIENTE',
    estado: 'PENDIENTE_PAGO',
    motivoCancelacion: null,
    comprobanteArchivoId: null,
    pagoConfirmadoEn: null,
    pagoConfirmadoPor: null,
    version: 0,
    creadoEn: '2026-08-11T09:00:00',
    actualizadoEn: '2026-08-11T09:00:00',
    historial: [],
    ...overrides,
  };
}
