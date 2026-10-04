import { TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { AdminCompany, CompanyCreateInput } from '../../core/models/admin.models';
import { AdminService } from '../../core/services/admin.service';
import { AuthService } from '../../core/services/auth.service';
import { SuperAdminPage } from './super-admin.page';

describe('SuperAdminPage', () => {
  it('generates a strong system password as soon as a new company is opened', () => {
    const component = createAdminPage();

    component.openCompany();

    expect(component.companyForm.controls.adminPassword.value).toHaveLength(20);
    expect(component.companyForm.controls.adminPassword.value)
      .toMatch(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9])\S{20}$/);
  });

  it('uses the company contact and administrator name to prepare the access account', () => {
    const component = createAdminPage();
    component.openCompany();
    component.companyForm.patchValue({
      codigo: 'NEX',
      nombre: 'Nexora Inmobiliaria',
      nit: '900123456',
      correo: 'admin@nexora.test',
    });

    component.nextCompanyStep();
    component.companyForm.patchValue({ adminNombres: 'Ana', adminApellidos: 'Pérez' });

    expect(component.companyWizardStep()).toBe(2);
    expect(component.companyForm.controls.adminCorreo.value).toBe('admin@nexora.test');
    expect(component.companyForm.controls.adminUsuario.value).toBe('ana.perez');
  });

  it('creates the administrator together with the company and exposes its generated credentials', () => {
    let submittedPayload: CompanyCreateInput | null = null;
    const company = { id: 7, nombre: 'Nexora Inmobiliaria' } as AdminCompany;
    const component = createAdminPage({
      createCompany: (payload: CompanyCreateInput) => {
        submittedPayload = payload;
        return of(company);
      },
    });

    component.openCompany();
    component.companyForm.patchValue({
      codigo: 'NEX',
      nombre: 'Nexora Inmobiliaria',
      nit: '900123456',
      correo: 'admin@nexora.test',
      adminDocumento: '1002245536',
      adminNombres: 'Ana',
      adminApellidos: 'Pérez',
      adminCorreo: 'admin@nexora.test',
    });
    component.companyConfirmation.set(true);

    component.saveCompany();

    expect(submittedPayload).toEqual(expect.objectContaining({
      administrador: expect.objectContaining({
        nombreUsuario: 'ana.perez',
        passwordTemporal: component.companyForm.controls.adminPassword.value,
      }),
    }));
    expect(component.companyCreated()).toBe(company);
    expect(component.issuedCredentials()).toEqual(expect.objectContaining({
      nombreUsuario: 'ana.perez',
      correo: 'admin@nexora.test',
      password: component.companyForm.controls.adminPassword.value,
    }));
  });

  it('also generates a password when creating a regular company user', () => {
    const component = createAdminPage();
    component.selectedCompany.set({ id: 7, nombre: 'Nexora Inmobiliaria' } as AdminCompany);

    component.openUser();

    expect(component.userForm.controls.passwordTemporal.value).toHaveLength(20);
    expect(component.modal()).toBe('usuario');
  });

  it('starts in the consolidated global overview instead of hiding information inside one company', () => {
    const component = createAdminPage();

    expect(component.activeTab()).toBe('resumen');
    expect(component.globalScope()).toBe(true);
  });

  it('loads and orders projects from every company when the global button is selected', () => {
    const companies = [
      { id: 1, codigo: 'NEX', nombre: 'Nexora', estado: 'ACTIVA', modulos: [] },
      { id: 2, codigo: 'ALT', nombre: 'Altamar', estado: 'ACTIVA', modulos: [] },
    ] as unknown as AdminCompany[];
    const projects = vi.fn((companyId: number) => of({
      contenido: [{
        id: companyId * 10,
        codigo: companyId === 1 ? 'MAR' : 'ALB',
        nombre: companyId === 1 ? 'Marina' : 'Alborada',
      }],
    }));
    const component = createAdminPage({
      companies: () => of({ contenido: companies }),
      projects,
    });

    component.openGlobalSection('proyectos');

    expect(projects).toHaveBeenCalledWith(1);
    expect(projects).toHaveBeenCalledWith(2);
    expect(component.projects().map((project) => project.nombre)).toEqual(['Alborada', 'Marina']);
    expect(component.companyForProject(component.projects()[0])?.nombre).toBe('Altamar');
  });

  it('consolidates user accounts while retaining each account company boundary', () => {
    const companies = [
      { id: 1, codigo: 'NEX', nombre: 'Nexora', estado: 'ACTIVA', modulos: [] },
      { id: 2, codigo: 'ALT', nombre: 'Altamar', estado: 'ACTIVA', modulos: [] },
    ] as unknown as AdminCompany[];
    const users = vi.fn((companyId: number) => of([{
      id: companyId,
      empresaId: companyId,
      nombreCompleto: companyId === 1 ? 'Zora Martínez' : 'Ana Pérez',
    }]));
    const component = createAdminPage({
      companies: () => of({ contenido: companies }),
      users,
    });

    component.openGlobalSection('usuarios');

    expect(users).toHaveBeenCalledWith(1, '', '');
    expect(users).toHaveBeenCalledWith(2, '', '');
    expect(component.users().map((user) => user.nombreCompleto)).toEqual(['Ana Pérez', 'Zora Martínez']);
    expect(component.companyForUser(component.users()[0])?.id).toBe(2);
  });

  it('asks which company should own a new project when the global view contains several companies', () => {
    const companies = [
      { id: 1, codigo: 'NEX', nombre: 'Nexora', estado: 'ACTIVA', modulos: [] },
      { id: 2, codigo: 'ALT', nombre: 'Altamar', estado: 'ACTIVA', modulos: [] },
    ] as unknown as AdminCompany[];
    const component = createAdminPage({ companies: () => of({ contenido: companies }) });

    component.openProjectFor();

    expect(component.modal()).toBe('selector_empresa');
    expect(component.pendingCreation()).toBe('proyecto');

    component.selectCreationCompany(companies[1]);

    expect(component.selectedCompany()?.id).toBe(2);
    expect(component.modal()).toBe('proyecto');
  });
});

function createAdminPage(overrides: Record<string, unknown> = {}): SuperAdminPage {
  const summary = {
    empresas: 0,
    empresasActivas: 0,
    empresasSuspendidas: 0,
    empresasInactivas: 0,
    proyectos: 0,
    usuarios: 0,
    usuariosActivos: 0,
  };
  const emptyPage = {
    contenido: [],
    pagina: 0,
    tamano: 20,
    totalElementos: 0,
    totalPaginas: 0,
    primera: true,
    ultima: true,
  };

  TestBed.configureTestingModule({
    providers: [
      FormBuilder,
      {
        provide: AdminService,
        useValue: {
          summary: () => of(summary),
          companies: () => of(emptyPage),
          analytics: () => of(null),
          ...overrides,
        },
      },
      { provide: AuthService, useValue: {} },
      { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
    ],
  });

  return TestBed.runInInjectionContext(() => new SuperAdminPage());
}
