import { TestBed } from '@angular/core/testing';

import { InsightChartComponent } from './insight-chart.component';

describe('InsightChartComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [InsightChartComponent] });
  });

  it('calculates a real proportional distribution for the donut chart', () => {
    const fixture = TestBed.createComponent(InsightChartComponent);
    fixture.componentRef.setInput('points', [
      { categoria: 'DISPONIBLE', cantidad: 3, valor: 3 },
      { categoria: 'VENDIDO', cantidad: 1, valor: 1 },
    ]);
    fixture.componentRef.setInput('variant', 'donut');
    fixture.detectChanges();

    expect(fixture.componentInstance.total()).toBe(4);
    expect(fixture.componentInstance.donutGradient()).toContain('75%');
    expect(fixture.nativeElement.textContent).toContain('Disponible');
    expect(fixture.nativeElement.textContent).toContain('Vendido');
  });

  it('renders genuine monetary values using Colombian thousands separators', () => {
    const fixture = TestBed.createComponent(InsightChartComponent);
    fixture.componentRef.setInput('points', [
      { categoria: '2026-08', cantidad: 2, valor: 65000000 },
    ]);
    fixture.componentRef.setInput('format', 'currency');
    fixture.detectChanges();

    expect(fixture.componentInstance.formatPoint({ categoria: 'Ventas', cantidad: 2, valor: 65000000 }))
      .toContain('65.000.000');
    expect(fixture.componentInstance.label('2026-08')).toContain('ago');
  });
});
