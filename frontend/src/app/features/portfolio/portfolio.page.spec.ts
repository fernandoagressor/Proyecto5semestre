import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { FollowUpInstallment } from '../../core/models/portfolio.models';
import { AuthService } from '../../core/services/auth.service';
import { PortfolioService } from '../../core/services/portfolio.service';
import { SalesService } from '../../core/services/sales.service';
import { PortfolioPage } from './portfolio.page';

describe('PortfolioPage', () => {
  it('registers preventive management from a partial installment before it is overdue', async () => {
    const partial = followUpInstallment();
    const promiseDate = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
    let createdPayload: Record<string, unknown> | null = null;
    const portfolio = {
      followUpInstallments: () => of(page([partial])),
      collections: () => of([]),
      createCollection: (payload: Record<string, unknown>) => {
        createdPayload = payload;
        return of({
          id: 90,
          ...payload,
          gestorId: 11,
          fechaGestion: '2026-08-11T16:00:00',
        });
      },
    };

    await TestBed.configureTestingModule({
      imports: [PortfolioPage],
      providers: [
        {
          provide: AuthService,
          useValue: {
            hasAuthority: (authority: string) => ['MORA_VER', 'MORA_GESTIONAR'].includes(authority),
          },
        },
        { provide: PortfolioService, useValue: portfolio },
        { provide: SalesService, useValue: {} },
      ],
    }).compileComponents();

    const fixture: ComponentFixture<PortfolioPage> = TestBed.createComponent(PortfolioPage);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    component.setTab('mora');
    fixture.detectChanges();

    const openButton = fixture.nativeElement.querySelector(
      '.table-actions .accent',
    ) as HTMLButtonElement;
    expect(openButton).toBeTruthy();
    openButton.click();
    fixture.detectChanges();

    expect(component.selectedFollowUp()?.estado).toBe('PARCIAL');
    expect(component.modal()).toBe('cobro');
    expect(fixture.nativeElement.textContent).toContain('GESTIÓN PREVENTIVA');

    component.collectionForm.patchValue({
      result: 'Contactado',
      detail: 'Acuerdo preventivo',
      promiseDate,
      promiseAmount: 500000,
    });
    fixture.detectChanges();
    const saveButton = fixture.nativeElement.querySelector(
      '.modal-card footer .primary-button',
    ) as HTMLButtonElement;
    saveButton.click();
    fixture.detectChanges();

    expect(createdPayload).toEqual({
      contratoId: 7,
      cuotaId: 70,
      canal: 'LLAMADA',
      resultado: 'Contactado',
      detalle: 'Acuerdo preventivo',
      promesaPagoFecha: promiseDate,
      promesaPagoValor: 500000,
    });
    expect(component.modal()).toBe('cobro');
    expect(component.collectionView()).toBe('historial');
    expect(component.feedback()).toBe('La gestión preventiva quedó registrada.');
    expect(component.selectedCollections().length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Acuerdo preventivo');
    expect(fixture.nativeElement.textContent).toContain('$500,000');
  });

  it('loads and displays the persisted history for the selected installment', async () => {
    const partial = followUpInstallment();
    const portfolio = {
      followUpInstallments: () => of(page([partial])),
      collections: () => of([{
        id: 91,
        contratoId: 7,
        cuotaId: 70,
        gestorId: 11,
        canal: 'WHATSAPP',
        resultado: 'Promesa acordada',
        detalle: 'Cliente confirma transferencia.',
        promesaPagoFecha: '2026-08-20',
        promesaPagoValor: 750000,
        fechaGestion: '2026-08-12T09:00:00',
      }]),
    };

    await TestBed.configureTestingModule({
      imports: [PortfolioPage],
      providers: [
        {
          provide: AuthService,
          useValue: {
            hasAuthority: (authority: string) => ['MORA_VER', 'MORA_GESTIONAR'].includes(authority),
          },
        },
        { provide: PortfolioService, useValue: portfolio },
        { provide: SalesService, useValue: {} },
      ],
    }).compileComponents();

    const fixture: ComponentFixture<PortfolioPage> = TestBed.createComponent(PortfolioPage);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    component.setTab('mora');
    fixture.detectChanges();

    const historyButton = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('.table-actions button'))
      .find((button) => button.textContent?.includes('Ver historial'));
    expect(historyButton).toBeTruthy();
    historyButton?.click();
    fixture.detectChanges();

    expect(component.collectionView()).toBe('historial');
    expect(fixture.nativeElement.textContent).toContain('Promesa acordada');
    expect(fixture.nativeElement.textContent).toContain('Cliente confirma transferencia.');
    expect(fixture.nativeElement.textContent).toContain('$750,000');
  });
});

function followUpInstallment(): FollowUpInstallment {
  return {
    cuotaId: 70,
    contratoId: 7,
    planPagoId: 4,
    numeroCuota: 4,
    fechaVencimiento: '2026-12-10',
    estado: 'PARCIAL',
    diasMora: 0,
    diasParaVencimiento: 121,
    saldo: 3916666,
    interesMora: 0,
    moraCalculadaHasta: null,
    totalPendiente: 3916666,
  };
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
