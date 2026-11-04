import { TestBed } from '@angular/core/testing';
import { FormControl } from '@angular/forms';

import { notificationLink } from '../../core/services/notification.service';
import { joinPhone, phoneValidator, PhoneInputComponent, splitPhone } from './phone-input.component';

describe('phone input', () => {
  it('splits stored phones into prefix and number', () => {
    expect(splitPhone('+57 3001234567')).toEqual({ prefix: '+57', number: '3001234567' });
    expect(splitPhone('+57 300 123 4567')).toEqual({ prefix: '+57', number: '3001234567' });
    expect(splitPhone('+5939876543')).toEqual({ prefix: '+593', number: '9876543' });
    expect(splitPhone('3001234567')).toEqual({ prefix: '+57', number: '3001234567' });
    expect(splitPhone(null)).toEqual({ prefix: '+57', number: '' });
  });

  it('joins prefix and at most 10 digits, or nothing when there is no number', () => {
    expect(joinPhone('+1', '305-555-0101')).toBe('+1 3055550101');
    expect(joinPhone('+57', '300123456789')).toBe('+57 3001234567');
    expect(joinPhone('+57', '')).toBe('');
  });

  it('accepts empty or 7 to 10 digits', () => {
    const valid = (value: string) => phoneValidator(new FormControl(value)) === null;
    expect(valid('')).toBe(true);
    expect(valid('+57 6041234')).toBe(true);
    expect(valid('+57 3001234567')).toBe(true);
    expect(valid('+57 123')).toBe(false);
    expect(valid('+57 30012345678')).toBe(false);
  });

  it('drops non-digits and anything past 10 digits while typing', () => {
    const component = TestBed.runInInjectionContext(() => new PhoneInputComponent());
    let value = '';
    component.registerOnChange((v) => (value = v));
    component.writeValue('+52 5512345678');
    const field = { value: '55-1234-5678-99' } as HTMLInputElement;

    component.setNumber(field);

    expect(field.value).toBe('5512345678');
    expect(value).toBe('+52 5512345678');
    component.setPrefix('+57');
    expect(value).toBe('+57 5512345678');
  });
});

describe('notificationLink', () => {
  it('opens the screen of the notified record', () => {
    expect(notificationLink({ entidadTipo: 'SOLICITUD_PQRS', entidadId: 12 })).toEqual({ path: '/servicio', queryParams: { pestana: 'solicitudes', solicitud: 12 } });
    expect(notificationLink({ entidadTipo: 'TICKET_SOPORTE', entidadId: 3 })).toEqual({ path: '/servicio', queryParams: { pestana: 'tickets', ticket: 3 } });
    expect(notificationLink({ entidadTipo: 'ALERTA_ANOMALIA', entidadId: 8 })).toEqual({ path: '/auditoria', queryParams: { alerta: 8 } });
    expect(notificationLink({ entidadTipo: 'OTRA_COSA', entidadId: 1 })).toBeNull();
  });
});
