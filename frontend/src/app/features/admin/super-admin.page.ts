import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, finalize, forkJoin, Observable, of } from 'rxjs';

import {
  AdminCompany,
  AdminProject,
  AdminRole,
  AdminUser,
  AdminUserCreateInput,
  AdminUserUpdateInput,
  CompanyCreateInput,
  CompanyStatus,
  CompanyUpdateInput,
  GlobalAdminAnalytics,
  GlobalAdminSummary,
  GlobalCompanyAnalytics,
  PermissionItem,
  ProjectInput,
  RoleInput,
  ScopeInput,
} from '../../core/models/admin.models';
import { ApiProblem, PageResponse } from '../../core/models/api.models';
import {
  ALL_COMPANY_MODULES,
  CompanyModuleCode,
} from '../../core/models/company-module.models';
import { AdminService } from '../../core/services/admin.service';
import { AuthService } from '../../core/services/auth.service';
import { AppIconComponent } from '../../shared/components/app-icon.component';
import { InsightChartComponent, InsightChartPoint } from '../../shared/components/insight-chart.component';

type AdminTab = 'resumen' | 'empresas' | 'proyectos' | 'usuarios' | 'roles';
type ModalType = 'empresa' | 'usuario' | 'rol' | 'proyecto' | 'selector_empresa' | null;
type CreationAction = 'proyecto' | 'usuario' | 'rol';
const STRONG_TEMPORARY_PASSWORD = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9])\S{12,72}$/;

interface IssuedCredentials {
  nombreUsuario: string;
  correo: string;
  password: string;
  empresa: string;
}

@Component({
  selector: 'app-super-admin-page',
  imports: [ReactiveFormsModule, AppIconComponent, InsightChartComponent],
  templateUrl: './super-admin.page.html',
  styleUrls: ['./super-admin.page.scss', './company-wizard.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SuperAdminPage {
  private readonly admin = inject(AdminService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private suggestedAdministratorUsername = '';

  readonly activeTab = signal<AdminTab>('resumen');
  readonly summary = signal<GlobalAdminSummary | null>(null);
  readonly analytics = signal<GlobalAdminAnalytics | null>(null);
  readonly companies = signal<PageResponse<AdminCompany> | null>(null);
  readonly selectedCompany = signal<AdminCompany | null>(null);
  readonly globalScope = signal(true);
  readonly projects = signal<AdminProject[]>([]);
  readonly users = signal<AdminUser[]>([]);
  readonly roles = signal<AdminRole[]>([]);
  readonly projectOwners = signal<Record<number, AdminCompany>>({});
  readonly roleOwners = signal<Record<number, AdminCompany>>({});
  readonly permissions = signal<PermissionItem[]>([]);
  readonly loading = signal(true);
  readonly sectionLoading = signal(false);
  readonly saving = signal(false);
  readonly switching = signal<number | null>(null);
  readonly modal = signal<ModalType>(null);
  readonly pendingCreation = signal<CreationAction | null>(null);
  readonly editingCompany = signal<AdminCompany | null>(null);
  readonly editingUser = signal<AdminUser | null>(null);
  readonly editingRole = signal<AdminRole | null>(null);
  readonly editingProject = signal<AdminProject | null>(null);
  readonly selectedUserRoles = signal<Set<number>>(new Set());
  readonly selectedPermissions = signal<Set<number>>(new Set());
  readonly companyWizardStep = signal(1);
  readonly companyModules = signal<Set<CompanyModuleCode>>(new Set(ALL_COMPANY_MODULES));
  readonly companyCreated = signal<AdminCompany | null>(null);
  readonly companyConfirmation = signal(false);
  readonly showTemporaryPassword = signal(false);
  readonly issuedCredentials = signal<IssuedCredentials | null>(null);
  readonly toast = signal<{ type: 'success' | 'error'; text: string } | null>(null);

  readonly moduleOptions: readonly {
    code: CompanyModuleCode;
    name: string;
    description: string;
    icon: string;
    required?: boolean;
  }[] = [
    {
      code: 'ADMINISTRACION_SEGURIDAD',
      name: 'Administración y seguridad',
      description: 'Usuarios, roles, permisos y auditoría.',
      icon: 'audit',
      required: true,
    },
    {
      code: 'PROYECTOS_INVENTARIO',
      name: 'Proyectos e inventario',
      description: 'Proyectos, etapas, manzanas y lotes.',
      icon: 'projects',
    },
    {
      code: 'CLIENTES_VENTAS',
      name: 'Clientes y ventas',
      description: 'Clientes, selección de lotes y reservas.',
      icon: 'crm',
    },
    {
      code: 'RESERVAS_CONTRATOS',
      name: 'Reservas y contratos',
      description: 'Documentos, firmas y contratos.',
      icon: 'sales',
    },
    {
      code: 'CARTERA_PAGOS',
      name: 'Cartera y pagos',
      description: 'Cuotas, pagos, mora y conciliación.',
      icon: 'wallet',
    },
    {
      code: 'PORTAL_SERVICIO',
      name: 'Portal y servicio al cliente',
      description: 'Portal, PQRS, citas y comunicaciones.',
      icon: 'service',
    },
    {
      code: 'GERENCIA_REPORTES',
      name: 'Gerencia y reportes',
      description: 'Indicadores, metas, comisiones y reportes.',
      icon: 'management',
    },
  ];

  readonly activeModuleCount = computed(() => this.companyModules().size);

  readonly companyStatusSeries = computed<InsightChartPoint[]>(() => {
    const summary = this.summary();
    if (!summary) return [];
    return this.counterSeries([
      ['Activas', summary.empresasActivas],
      ['Suspendidas', summary.empresasSuspendidas],
      ['Inactivas', summary.empresasInactivas],
    ]);
  });

  readonly globalOperationsSeries = computed<InsightChartPoint[]>(() => {
    const analytics = this.analytics();
    const summary = this.summary();
    if (!analytics || !summary) return [];
    return this.counterSeries([
      ['Proyectos', summary.proyectos],
      ['Usuarios', summary.usuarios],
      ['Lotes disponibles', analytics.lotesDisponibles],
      ['Lotes reservados', analytics.lotesReservados],
      ['Contratos vigentes', analytics.contratosVigentes],
      ['Reservas activas', analytics.reservasActivas],
    ]);
  });

  readonly companyRanking = computed<InsightChartPoint[]>(() => (this.analytics()?.empresas ?? [])
    .slice(0, 7)
    .map((company) => ({ categoria: company.nombre, valor: Number(company.ventas), cantidad: company.contratosVigentes })));

  readonly regionalSeries = computed<InsightChartPoint[]>(() => {
    const totals = new Map<string, number>();
    for (const company of this.companies()?.contenido ?? []) {
      const location = company.ciudad?.trim() || 'Sin ciudad';
      totals.set(location, (totals.get(location) ?? 0) + 1);
    }
    return [...totals.entries()]
      .sort((left, right) => right[1] - left[1])
      .slice(0, 6)
      .map(([categoria, cantidad]) => ({ categoria, cantidad, valor: cantidad }));
  });

  readonly moduleAdoptionSeries = computed<InsightChartPoint[]>(() => {
    const companies = this.companies()?.contenido ?? [];
    return this.moduleOptions.map((option) => {
      const count = companies.filter((company) => company.modulos?.includes(option.code)).length;
      return { categoria: option.name, valor: count, cantidad: count };
    }).filter((item) => item.cantidad > 0);
  });

  readonly globalHealth = computed(() => {
    const summary = this.summary();
    const analytics = this.analytics();
    if (!summary) return [];
    const activeCompanies = this.ratio(summary.empresasActivas, summary.empresas);
    const activeUsers = this.ratio(summary.usuariosActivos, summary.usuarios);
    const collection = analytics ? this.ratio(analytics.recaudo, analytics.ventas) : 0;
    const overdue = analytics ? this.ratio(analytics.carteraVencida, analytics.cartera) : 0;
    return [
      { label: 'Empresas activas', value: activeCompanies, tone: 'green', detail: `${summary.empresasActivas} de ${summary.empresas} habilitadas` },
      { label: 'Usuarios activos', value: activeUsers, tone: 'blue', detail: `${summary.usuariosActivos} cuentas vigentes` },
      { label: 'Cobertura de recaudo', value: collection, tone: 'violet', detail: 'Recaudo confirmado frente a ventas' },
      { label: 'Cartera saludable', value: Math.max(0, 100 - overdue), tone: 'green', detail: `${this.percent(overdue)} de cartera vencida` },
    ];
  });

  readonly companySearch = this.fb.nonNullable.control('');
  readonly companyStatus = this.fb.nonNullable.control('');
  readonly userSearch = this.fb.nonNullable.control('');
  readonly userStatus = this.fb.nonNullable.control('');

  readonly companyForm = this.fb.nonNullable.group({
    codigo: ['', [Validators.required, Validators.maxLength(30)]],
    nombre: ['', [Validators.required, Validators.maxLength(160)]],
    nit: ['', [Validators.required, Validators.maxLength(30)]],
    direccion: [''],
    ciudad: [''],
    departamento: [''],
    telefono: [''],
    correo: ['', Validators.email],
    logoUrl: [''],
    zonaHoraria: ['America/Bogota', Validators.required],
    moneda: ['COP', [Validators.required, Validators.pattern(/^[A-Z]{3}$/)]],
    limiteProyectos: [5, [Validators.required, Validators.min(1)]],
    limiteUsuarios: [20, [Validators.required, Validators.min(1)]],
    adminTipoDocumento: ['CC'],
    adminDocumento: [''],
    adminNombres: [''],
    adminApellidos: [''],
    adminUsuario: [''],
    adminCorreo: ['', Validators.email],
    adminPassword: [''],
  });

  readonly userForm = this.fb.nonNullable.group({
    tipoDocumento: ['CC'],
    numeroDocumento: [''],
    nombres: ['', Validators.required],
    apellidos: [''],
    telefono: [''],
    nombreUsuario: [''],
    correo: ['', [Validators.required, Validators.email]],
    passwordTemporal: [''],
    alcanceTipo: ['EMPRESA' as ScopeInput['tipo']],
    proyectoId: this.fb.control<number | null>(null),
  });

  readonly roleForm = this.fb.nonNullable.group({
    codigo: ['', Validators.required],
    nombre: ['', Validators.required],
    descripcion: [''],
    activo: [true],
  });

  readonly projectForm = this.fb.nonNullable.group({
    codigo: ['', Validators.required],
    nombre: ['', Validators.required],
    descripcion: [''],
    ciudad: ['', Validators.required],
    departamento: ['', Validators.required],
    direccion: [''],
    fechaInicio: [''],
    fechaFinEstimada: [''],
    maxLotes: this.fb.control<number | null>(null),
    imagenPrincipalUrl: [''],
    gerenteId: this.fb.control<number | null>(null),
  });

  readonly permissionGroups = computed(() => {
    const groups = new Map<string, PermissionItem[]>();
    for (const permission of this.permissions()) {
      const values = groups.get(permission.modulo) ?? [];
      values.push(permission);
      groups.set(permission.modulo, values);
    }
    return [...groups.entries()].map(([module, items]) => ({ module, items }));
  });

  constructor() {
    this.companySearch.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.loadCompanies());
    this.companyStatus.valueChanges
      .pipe(distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.loadCompanies());
    this.userSearch.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.loadUsers());
    this.userStatus.valueChanges
      .pipe(distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.loadUsers());
    this.companyForm.controls.adminNombres.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.suggestAdministratorUsername());
    this.companyForm.controls.adminApellidos.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.suggestAdministratorUsername());
    this.loadInitial();
  }

  selectTab(tab: AdminTab): void {
    this.activeTab.set(tab);
    this.closeModal();
    this.loadActiveSection();
  }

  selectCompany(id: string): void {
    const company = id ? this.companies()?.contenido.find((item) => item.id === Number(id)) ?? null : null;
    this.globalScope.set(!company);
    this.selectedCompany.set(company);
    this.loadActiveSection();
  }

  openGlobalSection(tab: AdminTab): void {
    this.globalScope.set(true);
    this.selectedCompany.set(null);
    this.selectTab(tab);
  }

  focusCompany(company: AdminCompany, tab: AdminTab = 'empresas'): void {
    this.globalScope.set(false);
    this.selectedCompany.set(company);
    this.selectTab(tab);
  }

  openCompanyAnalytics(companyId: number, tab: AdminTab = 'proyectos'): void {
    const company = this.companies()?.contenido.find((item) => item.id === companyId) ?? null;
    if (company) this.focusCompany(company, tab);
  }

  refreshGlobal(): void {
    this.loadInitial();
  }

  companyForProject(project: AdminProject): AdminCompany | null {
    return this.projectOwners()[project.id] ?? this.selectedCompany();
  }

  companyForUser(user: AdminUser): AdminCompany | null {
    return this.companies()?.contenido.find((company) => company.id === user.empresaId) ?? null;
  }

  companyForRole(role: AdminRole): AdminCompany | null {
    return this.roleOwners()[role.id] ?? this.companies()?.contenido.find((company) => company.id === role.empresaId) ?? null;
  }

  openProjectFor(project?: AdminProject): void {
    if (!project && this.requestCompanySelection('proyecto')) return;
    const company = project ? this.companyForProject(project) : this.ensureCompanyContext();
    if (!company) return;
    this.globalScope.set(false);
    this.selectedCompany.set(company);
    this.openProject(project);
  }

  openUserFor(user?: AdminUser): void {
    if (!user && this.requestCompanySelection('usuario')) return;
    const company = user ? this.companyForUser(user) : this.ensureCompanyContext();
    if (!company) return;
    this.globalScope.set(false);
    this.selectedCompany.set(company);
    this.loadUserWorkspace();
    this.openUser(user);
  }

  openRoleFor(role?: AdminRole): void {
    if (!role && this.requestCompanySelection('rol')) return;
    const company = role ? this.companyForRole(role) : this.ensureCompanyContext();
    if (!company) return;
    this.globalScope.set(false);
    this.selectedCompany.set(company);
    this.openRole(role);
  }

  resetPasswordFor(user: AdminUser): void {
    const company = this.companyForUser(user);
    if (!company) return;
    this.selectedCompany.set(company);
    this.resetPassword(user);
  }

  changeUserStatusFor(user: AdminUser, status: AdminUser['estado']): void {
    const company = this.companyForUser(user);
    if (!company) return;
    this.selectedCompany.set(company);
    this.changeUserStatus(user, status);
  }

  enterProject(project: AdminProject): void {
    const company = this.companyForProject(project);
    if (company) this.enterCompany(company, '/inventario');
  }

  selectCreationCompany(company: AdminCompany): void {
    if (company.estado !== 'ACTIVA') return;
    const action = this.pendingCreation();
    this.pendingCreation.set(null);
    this.globalScope.set(false);
    this.selectedCompany.set(company);
    if (action === 'proyecto') this.openProjectFor();
    if (action === 'usuario') this.openUserFor();
    if (action === 'rol') this.openRoleFor();
  }

  financialTotal(company: GlobalCompanyAnalytics): string {
    return this.money(company.ventas);
  }

  money(value: number | null | undefined): string {
    return new Intl.NumberFormat('es-CO', {
      style: 'currency', currency: 'COP', maximumFractionDigits: 0,
      notation: Math.abs(Number(value ?? 0)) >= 1_000_000_000 ? 'compact' : 'standard',
    }).format(Number(value ?? 0));
  }

  number(value: number | null | undefined): string {
    return new Intl.NumberFormat('es-CO').format(Number(value ?? 0));
  }

  percent(value: number): string {
    return `${new Intl.NumberFormat('es-CO', { maximumFractionDigits: 1 }).format(value)} %`;
  }

  openCompany(company?: AdminCompany): void {
    this.editingCompany.set(company ?? null);
    this.companyWizardStep.set(1);
    this.companyCreated.set(null);
    this.companyConfirmation.set(false);
    this.showTemporaryPassword.set(false);
    this.suggestedAdministratorUsername = '';
    this.companyModules.set(new Set(company?.modulos?.length ? company.modulos : ALL_COMPANY_MODULES));
    this.companyForm.reset({
      codigo: company?.codigo ?? '',
      nombre: company?.nombre ?? '',
      nit: company?.nit ?? '',
      direccion: company?.direccion ?? '',
      ciudad: company?.ciudad ?? '',
      departamento: company?.departamento ?? '',
      telefono: company?.telefono ?? '',
      correo: company?.correo ?? '',
      logoUrl: company?.logoUrl ?? '',
      zonaHoraria: company?.zonaHoraria ?? 'America/Bogota',
      moneda: company?.moneda ?? 'COP',
      limiteProyectos: company?.limiteProyectos ?? 5,
      limiteUsuarios: company?.limiteUsuarios ?? 20,
      adminTipoDocumento: 'CC',
      adminDocumento: '',
      adminNombres: '',
      adminApellidos: '',
      adminUsuario: '',
      adminCorreo: '',
      adminPassword: '',
    });
    if (!company) {
      this.issuedCredentials.set(null);
      this.generateAutomaticPassword('empresa');
    }
    this.modal.set('empresa');
  }

  nextCompanyStep(): void {
    const step = this.companyWizardStep();
    if (step === 1 && !this.validCompanyInformation()) {
      this.showError('Completa código, nombre y NIT, y revisa el correo de contacto.');
      return;
    }
    if (step === 1) this.prepareInitialAdministrator();
    if (step === 2 && !this.validInitialAdministrator()) {
      this.showError('Completa los datos del administrador; el sistema generará su contraseña automáticamente.');
      return;
    }
    if (step === 3 && !this.validLimits()) {
      this.showError('Los límites de proyectos y usuarios deben ser mayores que cero.');
      return;
    }
    this.companyWizardStep.set(Math.min(step + 1, 4));
  }

  previousCompanyStep(): void {
    this.companyWizardStep.update((step) => Math.max(step - 1, 1));
  }

  toggleCompanyModule(code: CompanyModuleCode): void {
    if (code === 'ADMINISTRACION_SEGURIDAD') return;
    const modules = new Set(this.companyModules());
    modules.has(code) ? modules.delete(code) : modules.add(code);
    modules.add('ADMINISTRACION_SEGURIDAD');
    this.companyModules.set(modules);
  }

  generateAutomaticPassword(target: 'empresa' | 'usuario' = 'empresa'): void {
    const password = this.createAutomaticPassword();
    if (target === 'empresa') {
      this.companyForm.controls.adminPassword.setValue(password);
    } else {
      this.userForm.controls.passwordTemporal.setValue(password);
    }
    this.showTemporaryPassword.set(false);
  }

  copyCredential(value: string): void {
    const clipboard = globalThis.navigator?.clipboard;
    if (!clipboard) {
      this.showError('Tu navegador no permite copiar automáticamente. Selecciona y copia el dato.');
      return;
    }
    void clipboard.writeText(value)
      .then(() => this.showSuccess('Dato de acceso copiado.'))
      .catch(() => this.showError('No fue posible copiar el dato de acceso.'));
  }

  saveCompany(): void {
    if (this.companyForm.invalid || this.saving()) {
      this.showError('Revisa los campos obligatorios de la empresa.');
      return;
    }
    const value = this.companyForm.getRawValue();
    const editing = this.editingCompany();
    if (!editing && !this.companyConfirmation()) {
      this.showError('Confirma que revisaste la información antes de crear la empresa.');
      return;
    }
    const base = {
      codigo: value.codigo.trim(),
      nombre: value.nombre.trim(),
      nit: value.nit.trim(),
      direccion: this.optional(value.direccion),
      ciudad: this.optional(value.ciudad),
      departamento: this.optional(value.departamento),
      telefono: this.optional(value.telefono),
      correo: this.optional(value.correo),
      logoUrl: this.optional(value.logoUrl),
      zonaHoraria: value.zonaHoraria.trim(),
      moneda: value.moneda.trim().toUpperCase(),
      limiteProyectos: Number(value.limiteProyectos),
      limiteUsuarios: Number(value.limiteUsuarios),
      modulos: [...this.companyModules()],
    };

    let request;
    if (editing) {
      request = this.admin.updateCompany(editing.id, { ...base, version: editing.version } satisfies CompanyUpdateInput);
    } else {
      if (!value.adminDocumento.trim() || !value.adminNombres.trim() || !value.adminUsuario.trim() ||
          !value.adminCorreo.trim() || !STRONG_TEMPORARY_PASSWORD.test(value.adminPassword)) {
        this.showError('Completa los datos del administrador inicial para generar su acceso seguro.');
        return;
      }
      const payload: CompanyCreateInput = {
        ...base,
        administrador: {
          tipoDocumento: value.adminTipoDocumento.trim(),
          numeroDocumento: value.adminDocumento.trim(),
          nombres: value.adminNombres.trim(),
          apellidos: this.optional(value.adminApellidos),
          nombreUsuario: value.adminUsuario.trim(),
          correo: value.adminCorreo.trim(),
          passwordTemporal: value.adminPassword,
        },
      };
      request = this.admin.createCompany(payload);
    }
    this.saving.set(true);
    request
      .pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (company) => {
          this.selectedCompany.set(company);
          if (editing) {
            this.closeModal();
            this.showSuccess('Empresa actualizada.');
          } else {
            this.issuedCredentials.set({
              nombreUsuario: value.adminUsuario.trim(),
              correo: value.adminCorreo.trim(),
              password: value.adminPassword,
              empresa: company.nombre,
            });
            this.companyCreated.set(company);
            this.showSuccess('Empresa creada y usuario administrador generado automáticamente.');
          }
          this.reloadGlobal();
        },
        error: (error) => this.showApiError(error),
      });
  }

  changeCompanyStatus(company: AdminCompany, status: CompanyStatus): void {
    const label = status === 'ACTIVA' ? 'activar' : status === 'SUSPENDIDA' ? 'suspender' : 'inactivar';
    if (!globalThis.confirm(`¿Deseas ${label} la empresa ${company.nombre}?`)) return;
    this.admin.changeCompanyStatus(company, status, `Cambio a ${status} confirmado desde la consola global`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.showSuccess(`Empresa ${status.toLowerCase()}.`);
          this.reloadGlobal();
        },
        error: (error) => this.showApiError(error),
      });
  }

  enterCompany(company: AdminCompany, route = '/dashboard'): void {
    if (company.estado !== 'ACTIVA' || this.switching()) return;
    this.switching.set(company.id);
    this.auth.switchCompany(company.id)
      .pipe(finalize(() => this.switching.set(null)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => void this.router.navigate([route]),
        error: (error) => this.showApiError(error),
      });
  }

  openUser(user?: AdminUser): void {
    const company = this.selectedCompany();
    if (!company) return;
    this.editingUser.set(user ?? null);
    this.selectedUserRoles.set(new Set(user?.rolIds ?? []));
    const scope = user?.alcances[0];
    this.userForm.reset({
      tipoDocumento: user?.tipoDocumento ?? 'CC',
      numeroDocumento: user?.numeroDocumento ?? '',
      nombres: user?.nombres ?? '',
      apellidos: user?.apellidos ?? '',
      telefono: user?.telefono ?? '',
      nombreUsuario: user?.nombreUsuario ?? '',
      correo: user?.correo ?? '',
      passwordTemporal: '',
      alcanceTipo: scope?.tipo ?? 'EMPRESA',
      proyectoId: scope?.proyectoId ?? null,
    });
    if (!user) this.generateAutomaticPassword('usuario');
    this.modal.set('usuario');
  }

  toggleUserRole(roleId: number): void {
    const next = new Set(this.selectedUserRoles());
    next.has(roleId) ? next.delete(roleId) : next.add(roleId);
    this.selectedUserRoles.set(next);
  }

  saveUser(): void {
    const company = this.selectedCompany();
    const roles = [...this.selectedUserRoles()];
    if (!company || this.userForm.invalid || roles.length === 0 || this.saving()) {
      this.showError('Completa los datos y selecciona al menos un rol.');
      return;
    }
    const value = this.userForm.getRawValue();
    const editing = this.editingUser();
    const scope: ScopeInput = {
      tipo: value.alcanceTipo,
      proyectoId: value.alcanceTipo === 'PROYECTO' ? value.proyectoId : null,
    };
    if (scope.tipo === 'PROYECTO' && !scope.proyectoId) {
      this.showError('Selecciona el proyecto autorizado para el usuario.');
      return;
    }
    let request;
    if (editing) {
      const payload: AdminUserUpdateInput = {
        nombres: value.nombres.trim(),
        apellidos: this.optional(value.apellidos),
        telefono: this.optional(value.telefono),
        correo: value.correo.trim(),
        rolIds: roles,
        alcances: [scope],
        version: editing.version,
      };
      request = this.admin.updateUser(company.id, editing.id, payload);
    } else {
      if (!value.numeroDocumento.trim() || !value.nombreUsuario.trim() ||
          !STRONG_TEMPORARY_PASSWORD.test(value.passwordTemporal)) {
        this.showError('Completa el documento y el usuario para generar automáticamente sus credenciales.');
        return;
      }
      const payload: AdminUserCreateInput = {
        tipoDocumento: value.tipoDocumento,
        numeroDocumento: value.numeroDocumento.trim(),
        nombres: value.nombres.trim(),
        apellidos: this.optional(value.apellidos),
        telefono: this.optional(value.telefono),
        nombreUsuario: value.nombreUsuario.trim(),
        correo: value.correo.trim(),
        passwordTemporal: value.passwordTemporal,
        rolIds: roles,
        alcances: [scope],
      };
      request = this.admin.createUser(company.id, payload);
    }
    this.saving.set(true);
    request.pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.closeModal();
        if (!editing) {
          this.issuedCredentials.set({
            nombreUsuario: value.nombreUsuario.trim(),
            correo: value.correo.trim(),
            password: value.passwordTemporal,
            empresa: company.nombre,
          });
        }
        this.showSuccess(editing ? 'Usuario actualizado.' : 'Usuario creado con contraseña automática.');
        this.loadUsers();
        this.loadSummary();
      },
      error: (error) => this.showApiError(error),
    });
  }

  changeUserStatus(user: AdminUser, status: AdminUser['estado']): void {
    const company = this.selectedCompany();
    if (!company || !globalThis.confirm(`¿Confirmas cambiar a ${status} el usuario ${user.nombreUsuario}?`)) return;
    this.admin.changeUserStatus(company.id, user, status, `Cambio a ${status} desde la consola global`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.showSuccess('Estado del usuario actualizado.');
          this.loadUsers();
        },
        error: (error) => this.showApiError(error),
      });
  }

  resetPassword(user: AdminUser): void {
    const company = this.selectedCompany();
    if (!company) return;
    if (!globalThis.confirm(`¿Generar automáticamente una nueva contraseña segura para ${user.nombreUsuario}?`)) return;
    const password = this.createAutomaticPassword();
    this.admin.resetCredentials(company.id, user.id, password, 'Reinicio solicitado desde la consola global')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.issuedCredentials.set({
            nombreUsuario: user.nombreUsuario,
            correo: user.correo,
            password,
            empresa: company.nombre,
          });
          this.showSuccess('Nueva contraseña generada y sesiones anteriores cerradas.');
        },
        error: (error) => this.showApiError(error),
      });
  }

  openRole(role?: AdminRole): void {
    if (role?.sistema || role?.protegido) return;
    this.editingRole.set(role ?? null);
    const ids = this.permissions()
      .filter((permission) => role?.permisos.includes(permission.codigo))
      .map((permission) => permission.id);
    this.selectedPermissions.set(new Set(ids));
    this.roleForm.reset({
      codigo: role?.codigo ?? '',
      nombre: role?.nombre ?? '',
      descripcion: role?.descripcion ?? '',
      activo: role?.activo ?? true,
    });
    this.modal.set('rol');
  }

  togglePermission(permissionId: number): void {
    const next = new Set(this.selectedPermissions());
    next.has(permissionId) ? next.delete(permissionId) : next.add(permissionId);
    this.selectedPermissions.set(next);
  }

  saveRole(): void {
    const company = this.selectedCompany();
    if (!company || this.roleForm.invalid || this.saving()) return;
    const value = this.roleForm.getRawValue();
    const payload: RoleInput = {
      codigo: value.codigo.trim(),
      nombre: value.nombre.trim(),
      descripcion: this.optional(value.descripcion),
      activo: value.activo,
      permisoIds: [...this.selectedPermissions()],
    };
    const editing = this.editingRole();
    const request = editing
      ? this.admin.updateRole(company.id, editing.id, payload)
      : this.admin.createRole(company.id, payload);
    this.saving.set(true);
    request.pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.closeModal();
        this.showSuccess(editing ? 'Rol actualizado.' : 'Rol personalizado creado.');
        this.loadRoles();
      },
      error: (error) => this.showApiError(error),
    });
  }

  openProject(project?: AdminProject): void {
    this.editingProject.set(project ?? null);
    this.projectForm.reset({
      codigo: project?.codigo ?? '',
      nombre: project?.nombre ?? '',
      descripcion: project?.descripcion ?? '',
      ciudad: project?.ciudad ?? '',
      departamento: project?.departamento ?? '',
      direccion: project?.direccion ?? '',
      fechaInicio: project?.fechaInicio ?? '',
      fechaFinEstimada: project?.fechaFinEstimada ?? '',
      maxLotes: project?.maxLotes ?? null,
      imagenPrincipalUrl: project?.imagenPrincipalUrl ?? '',
      gerenteId: project?.gerenteId ?? null,
    });
    this.modal.set('proyecto');
  }

  saveProject(): void {
    const company = this.selectedCompany();
    if (!company || this.projectForm.invalid || this.saving()) return;
    const value = this.projectForm.getRawValue();
    const payload: ProjectInput = {
      codigo: value.codigo.trim(),
      nombre: value.nombre.trim(),
      descripcion: this.optional(value.descripcion),
      ciudad: value.ciudad.trim(),
      departamento: value.departamento.trim(),
      direccion: this.optional(value.direccion),
      fechaInicio: this.optional(value.fechaInicio),
      fechaFinEstimada: this.optional(value.fechaFinEstimada),
      maxLotes: value.maxLotes,
      imagenPrincipalUrl: this.optional(value.imagenPrincipalUrl),
      gerenteId: value.gerenteId,
    };
    this.saving.set(true);
    const editing = this.editingProject();
    const request = editing
      ? this.admin.updateProject(company.id, editing.id, payload)
      : this.admin.createProject(company.id, payload);
    request
      .pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.closeModal();
          this.showSuccess(editing ? 'Proyecto actualizado.' : 'Proyecto creado dentro de la empresa.');
          this.loadProjects();
          this.reloadGlobal();
        },
        error: (error) => this.showApiError(error),
      });
  }

  closeModal(): void {
    this.modal.set(null);
    this.pendingCreation.set(null);
    this.editingCompany.set(null);
    this.editingUser.set(null);
    this.editingRole.set(null);
    this.editingProject.set(null);
    this.companyCreated.set(null);
    this.companyConfirmation.set(false);
    this.companyWizardStep.set(1);
    this.showTemporaryPassword.set(false);
  }

  private loadInitial(): void {
    this.loading.set(true);
    forkJoin({
      summary: this.admin.summary(),
      companies: this.admin.companies({ size: 100 }),
      analytics: this.admin.analytics().pipe(catchError(() => of(null))),
    })
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ summary, companies, analytics }) => {
          this.summary.set(summary);
          this.analytics.set(analytics);
          this.companies.set(companies);
          if (!this.globalScope()) {
            const selected = this.selectedCompany();
            this.selectedCompany.set(
              companies.contenido.find((company) => company.id === selected?.id) ?? companies.contenido[0] ?? null,
            );
          }
          this.loadActiveSection();
        },
        error: (error) => this.showApiError(error),
      });
  }

  private reloadGlobal(): void {
    this.loadSummary();
    this.loadCompanies();
    this.admin.analytics().pipe(catchError(() => of(null)), takeUntilDestroyed(this.destroyRef))
      .subscribe((analytics) => { if (analytics) this.analytics.set(analytics); });
  }

  private loadSummary(): void {
    this.admin.summary().pipe(catchError(() => of(null)), takeUntilDestroyed(this.destroyRef))
      .subscribe((summary) => { if (summary) this.summary.set(summary); });
  }

  private loadCompanies(): void {
    this.sectionLoading.set(true);
    this.admin.companies({ text: this.companySearch.value.trim(), status: this.companyStatus.value, size: 100 })
      .pipe(finalize(() => this.sectionLoading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (companies) => {
          this.companies.set(companies);
          const selected = this.selectedCompany();
          if (selected) {
            this.selectedCompany.set(companies.contenido.find((company) => company.id === selected.id) ?? selected);
          }
        },
        error: (error) => this.showApiError(error),
      });
  }

  private loadActiveSection(): void {
    if (this.activeTab() === 'resumen' || this.activeTab() === 'empresas') return;
    if (this.globalScope()) {
      if (this.activeTab() === 'proyectos') this.loadGlobalProjects();
      if (this.activeTab() === 'usuarios') this.loadGlobalUsers();
      if (this.activeTab() === 'roles') this.loadGlobalRoles();
      return;
    }
    if (!this.selectedCompany()) return;
    if (this.activeTab() === 'proyectos') this.loadProjects();
    if (this.activeTab() === 'usuarios') this.loadUserWorkspace();
    if (this.activeTab() === 'roles') this.loadRoles();
  }

  private loadProjects(): void {
    const company = this.selectedCompany();
    if (!company) return;
    this.sectionLoading.set(true);
    this.admin.projects(company.id)
      .pipe(finalize(() => this.sectionLoading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.projects.set(page.contenido);
          this.projectOwners.set(Object.fromEntries(page.contenido.map((project) => [project.id, company])));
        },
        error: (error) => this.showApiError(error),
      });
  }

  private loadUsers(): void {
    if (this.activeTab() !== 'usuarios') return;
    if (this.globalScope()) {
      this.loadGlobalUsers();
      return;
    }
    const company = this.selectedCompany();
    if (!company) return;
    this.sectionLoading.set(true);
    this.admin.users(company.id, this.userSearch.value.trim(), this.userStatus.value)
      .pipe(finalize(() => this.sectionLoading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (users) => this.users.set(users), error: (error) => this.showApiError(error) });
  }

  private loadUserWorkspace(): void {
    const company = this.selectedCompany();
    if (!company) return;
    this.sectionLoading.set(true);
    forkJoin({
      users: this.admin.users(company.id, this.userSearch.value.trim(), this.userStatus.value),
      roles: this.admin.roles(company.id),
      projects: this.admin.projects(company.id),
    })
      .pipe(finalize(() => this.sectionLoading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ users, roles, projects }) => {
          this.users.set(users);
          this.roles.set(roles);
          this.projects.set(projects.contenido);
        },
        error: (error) => this.showApiError(error),
      });
  }

  private loadRoles(): void {
    const company = this.selectedCompany();
    if (!company) return;
    this.sectionLoading.set(true);
    forkJoin({ roles: this.admin.roles(company.id), permissions: this.admin.permissions() })
      .pipe(finalize(() => this.sectionLoading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ roles, permissions }) => { this.roles.set(roles); this.permissions.set(permissions); },
        error: (error) => this.showApiError(error),
      });
  }

  private loadGlobalProjects(): void {
    const companies = this.companies()?.contenido ?? [];
    if (companies.length === 0) {
      this.projects.set([]);
      this.projectOwners.set({});
      return;
    }
    this.sectionLoading.set(true);
    const requests: Record<string, Observable<PageResponse<AdminProject> | null>> = Object.fromEntries(
      companies.map((company) => [
        String(company.id),
        this.admin.projects(company.id).pipe(catchError(() => of(null))),
      ]),
    );
    forkJoin(requests)
      .pipe(finalize(() => this.sectionLoading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe((responses) => {
        const owners: Record<number, AdminCompany> = {};
        const projects = Object.entries(responses).flatMap(([companyId, response]) => {
          const company = companies.find((item) => item.id === Number(companyId));
          if (!company || !response) return [];
          for (const project of response.contenido) owners[project.id] = company;
          return response.contenido;
        }).sort((left, right) => left.nombre.localeCompare(right.nombre, 'es'));
        this.projectOwners.set(owners);
        this.projects.set(projects);
      });
  }

  private loadGlobalUsers(): void {
    const companies = this.companies()?.contenido ?? [];
    if (companies.length === 0) {
      this.users.set([]);
      return;
    }
    this.sectionLoading.set(true);
    const requests: Record<string, Observable<AdminUser[]>> = Object.fromEntries(
      companies.map((company) => [
        String(company.id),
        this.admin.users(company.id, this.userSearch.value.trim(), this.userStatus.value)
          .pipe(catchError(() => of([] as AdminUser[]))),
      ]),
    );
    forkJoin(requests)
      .pipe(finalize(() => this.sectionLoading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe((responses) => this.users.set(
        Object.values(responses).flat().sort((left, right) => left.nombreCompleto.localeCompare(right.nombreCompleto, 'es')),
      ));
  }

  private loadGlobalRoles(): void {
    const companies = this.companies()?.contenido ?? [];
    if (companies.length === 0) {
      this.roles.set([]);
      return;
    }
    this.sectionLoading.set(true);
    const requests: Record<string, Observable<AdminRole[]>> = Object.fromEntries(
      companies.map((company) => [
        String(company.id),
        this.admin.roles(company.id).pipe(catchError(() => of([] as AdminRole[]))),
      ]),
    );
    forkJoin(requests)
      .pipe(finalize(() => this.sectionLoading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe((responses) => {
        const unique = new Map<number, AdminRole>();
        const owners: Record<number, AdminCompany> = {};
        for (const [companyId, roles] of Object.entries(responses)) {
          const company = companies.find((item) => item.id === Number(companyId));
          for (const role of roles) {
            if (!unique.has(role.id)) unique.set(role.id, role);
            if (company && !role.sistema) owners[role.id] = company;
          }
        }
        this.roleOwners.set(owners);
        this.roles.set([...unique.values()].sort((left, right) => left.nombre.localeCompare(right.nombre, 'es')));
      });
    this.admin.permissions().pipe(catchError(() => of([] as PermissionItem[])), takeUntilDestroyed(this.destroyRef))
      .subscribe((permissions) => this.permissions.set(permissions));
  }

  private ensureCompanyContext(): AdminCompany | null {
    const selected = this.selectedCompany();
    if (selected && selected.estado === 'ACTIVA') return selected;
    const active = this.companies()?.contenido.find((company) => company.estado === 'ACTIVA') ?? null;
    if (!active) this.showError('No hay una empresa activa disponible para completar esta acción.');
    return active;
  }

  private requestCompanySelection(action: CreationAction): boolean {
    if (!this.globalScope()) return false;
    const activeCompanies = (this.companies()?.contenido ?? []).filter((company) => company.estado === 'ACTIVA');
    if (activeCompanies.length <= 1) return false;
    this.pendingCreation.set(action);
    this.modal.set('selector_empresa');
    return true;
  }

  private counterSeries(entries: Array<[string, number]>): InsightChartPoint[] {
    return entries.filter(([, value]) => Number(value) > 0)
      .map(([categoria, count]) => ({ categoria, cantidad: Number(count), valor: Number(count) }));
  }

  private ratio(value: number, total: number): number {
    if (!Number(total)) return 0;
    return Math.min(100, Math.max(0, Number(value) / Number(total) * 100));
  }

  private showSuccess(text: string): void {
    this.toast.set({ type: 'success', text });
    globalThis.setTimeout(() => this.toast.set(null), 3500);
  }

  private showError(text: string): void {
    this.toast.set({ type: 'error', text });
    globalThis.setTimeout(() => this.toast.set(null), 4500);
  }

  private showApiError(error: { error?: ApiProblem }): void {
    this.showError(error?.error?.detail ?? 'No fue posible completar la operación.');
  }

  private optional(value: string): string | null {
    return value?.trim() ? value.trim() : null;
  }

  private prepareInitialAdministrator(): void {
    const controls = this.companyForm.controls;
    if (!controls.adminCorreo.value.trim() && controls.correo.value.trim()) {
      controls.adminCorreo.setValue(controls.correo.value.trim());
    }
    this.suggestAdministratorUsername();
    if (!STRONG_TEMPORARY_PASSWORD.test(controls.adminPassword.value)) {
      this.generateAutomaticPassword('empresa');
    }
  }

  private suggestAdministratorUsername(): void {
    if (this.editingCompany()) return;

    const controls = this.companyForm.controls;
    const currentUsername = controls.adminUsuario.value.trim();
    if (currentUsername && currentUsername !== this.suggestedAdministratorUsername) return;

    const firstName = controls.adminNombres.value.trim().split(/\s+/)[0] ?? '';
    const lastName = controls.adminApellidos.value.trim().split(/\s+/)[0] ?? '';
    const companyCode = controls.codigo.value.trim();
    const base = firstName
      ? `${firstName}${lastName ? `.${lastName}` : ''}`
      : companyCode ? `admin.${companyCode}` : '';
    const suggestion = base.normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9._-]/g, '.')
      .replace(/\.{2,}/g, '.')
      .replace(/^\.+|\.+$/g, '')
      .slice(0, 80);

    if (!suggestion || suggestion.length < 3) return;
    this.suggestedAdministratorUsername = suggestion;
    controls.adminUsuario.setValue(suggestion, { emitEvent: false });
  }

  private createAutomaticPassword(): string {
    const uppercase = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
    const lowercase = 'abcdefghijkmnopqrstuvwxyz';
    const digits = '23456789';
    const symbols = '!@#$%&*?';
    const alphabet = uppercase + lowercase + digits + symbols;
    const random = new Uint32Array(20);
    globalThis.crypto.getRandomValues(random);

    const characters = [
      uppercase[random[0] % uppercase.length],
      lowercase[random[1] % lowercase.length],
      digits[random[2] % digits.length],
      symbols[random[3] % symbols.length],
    ];
    for (let index = 4; index < random.length; index += 1) {
      characters.push(alphabet[random[index] % alphabet.length]);
    }
    for (let index = characters.length - 1; index > 0; index -= 1) {
      const swapIndex = random[index] % (index + 1);
      [characters[index], characters[swapIndex]] = [characters[swapIndex], characters[index]];
    }
    return characters.join('');
  }

  private validCompanyInformation(): boolean {
    const controls = this.companyForm.controls;
    controls.codigo.markAsTouched();
    controls.nombre.markAsTouched();
    controls.nit.markAsTouched();
    controls.correo.markAsTouched();
    return controls.codigo.valid && controls.nombre.valid && controls.nit.valid && controls.correo.valid;
  }

  private validInitialAdministrator(): boolean {
    const value = this.companyForm.getRawValue();
    return Boolean(
      value.adminTipoDocumento.trim() &&
      value.adminDocumento.trim() &&
      value.adminNombres.trim() &&
      value.adminUsuario.trim().length >= 3 &&
      value.adminCorreo.trim() &&
      this.companyForm.controls.adminCorreo.valid &&
      STRONG_TEMPORARY_PASSWORD.test(value.adminPassword),
    );
  }

  private validLimits(): boolean {
    const value = this.companyForm.getRawValue();
    return Number(value.limiteProyectos) >= 1 && Number(value.limiteUsuarios) >= 1;
  }
}
