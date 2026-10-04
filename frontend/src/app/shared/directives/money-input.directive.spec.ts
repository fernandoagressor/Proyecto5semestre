import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { MoneyInputDirective } from './money-input.directive';

@Component({
  imports: [ReactiveFormsModule, MoneyInputDirective],
  template: '<input type="text" nexoraMoney [formControl]="amount" />',
})
class MoneyInputHostComponent {
  readonly amount = new FormControl<number | null>(null);
}

describe('MoneyInputDirective', () => {
  let fixture: ComponentFixture<MoneyInputHostComponent>;
  let input: HTMLInputElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MoneyInputHostComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(MoneyInputHostComponent);
    fixture.detectChanges();
    input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
  });

  it('adds Colombian thousands separators while preserving a numeric form value', () => {
    input.value = '1250000';
    input.dispatchEvent(new Event('input'));

    expect(input.value).toBe('1.250.000');
    expect(fixture.componentInstance.amount.value).toBe(1250000);
  });

  it('formats amounts assigned directly to the reactive form', () => {
    fixture.componentInstance.amount.setValue(98000000);
    fixture.detectChanges();

    expect(input.value).toBe('98.000.000');
  });

  it('clears the numeric value when the input is emptied', () => {
    fixture.componentInstance.amount.setValue(3000);
    input.value = '';
    input.dispatchEvent(new Event('input'));

    expect(input.value).toBe('');
    expect(fixture.componentInstance.amount.value).toBeNull();
  });
});
