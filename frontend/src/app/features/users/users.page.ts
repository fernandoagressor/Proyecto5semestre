import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, debounceTime, distinctUntilChanged, finalize, of } from 'rxjs';

import {
  AdminRole,
  AdminUser,
  AdminUserCreateInput,
  AdminUserUpdateInput,
  PermissionItem,
  RoleInput,
  ScopeInput,
} from '../../core/models/admin.models';
import { Project } from '../../core/models/empresa.models';
import { AuthService } from '../../core/services/auth.service';
import { CompanySecurityService } from '../../core/services/company-security.service';
import { ProjectService } from '../../core/services/project.service';
import { generateTemporaryPassword, STRONG_TEMPORARY_PASSWORD } from '../../core/utils/temporary-password';
import { apiErrorMessage, ChipOption, NX_UI, TabItem, ToastService } from '../../shared/ui';

interface IssuedCredentials {
  usuario: string;
  password: string;
  nuevo: boolean;
}

interface PermissionGroup {
  module: string;
  items: (PermissionItem & { owned: boolean })[];
}

const STATUS_FILTERS: ChipOption[] = [
  { value: '', label: 'Todos' },
  { value: 'ACTIVO', label: 'Activos' },
  { value: 'BLOQUEADO', label: 'Bloqueados' },
  { value: 'INACTIVO', label: 'Inactivos' },
];

const SCOPE_LABELS: Record<ScopeInput['tipo'], string> = {
  EMPRESA: 'Toda la empresa',
  PROYECTO: 'Un proyecto',
  PROPIOS: 'Solo sus registros',
};

/** Usuarios y roles (vista "Usuarios-Roles" del diseño) para el administrador de la empresa. */
@Component({
  selector: 'app-users-page',
  imports: [...NX_UI, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './users.page.html',
  styleUrl: './users.page.scss',
})
export class UsersPage {
  private readonly security = inject(CompanySecurityService);
  private readonly projectService = inject(ProjectService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);
  readonly auth = inject(AuthService);

  readonly canSeeUsers = this.auth.hasAuthority('USUARIOS_VER');
  readonly canSeeRoles = this.auth.hasAuthority('ROLES_VER');
  readonly canCreateUser = this.auth.hasAuthority('USUARIOS_CREAR');
  readonly canEditUser = this.auth.hasAuthority('USUARIOS_EDITAR');
  readonly canBlock = this.auth.hasAuthority('USUARIOS_BLOQUEAR');
  readonly canReset = this.auth.hasAuthority('USUARIOS_CREDENCIALES');
  readonly canCreateRole = this.auth.hasAuthority('ROLES_CREAR');
  readonly canEditRole = this.auth.hasAuthority('ROLES_EDITAR');
  readonly me = this.auth.user()?.id ?? 0;
  private readonly mine = new Set(this.auth.user()?.permisos ?? []);

  readonly statusFilters = STATUS_FILTERS;
  readonly scopeOptions: { value: ScopeInput['tipo']; label: string; hint: string }[] = [
    { value: 'EMPRESA', label: SCOPE_LABELS.EMPRESA, hint: 'Ve todos los proyectos y registros.' },
    { value: 'PROYECTO', label: SCOPE_LABELS.PROYECTO, hint: 'Solo lo de un proyecto.' },
    { value: 'PROPIOS', label: SCOPE_LABELS.PROPIOS, hint: 'Solo lo que él mismo registra o tiene asignado.' },
  ];
  readonly tab = signal<string>(this.canSeeUsers ? 'usuarios' : 'roles');
  readonly tabs = computed<TabItem[]>(() => [
    ...(this.canSeeUsers ? [{ id: 'usuarios', label: 'Usuarios', count: this.users().length }] : []),
    ...(this.canSeeRoles ? [{ id: 'roles', label: 'Roles y permisos', count: this.roles().length }] : []),
  ]);

  readonly search = new FormControl('', { nonNullable: true });
  readonly text = toSignal(this.search.valueChanges.pipe(debounceTime(300), distinctUntilChanged()), { initialValue: '' });
  readonly status = signal('');

  readonly users = signal<AdminUser[]>([]);
  readonly roles = signal<AdminRole[]>([]);
  readonly permissions = signal<PermissionItem[]>([]);
  readonly projects = signal<Project[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly saving = signal(false);
  readonly issued = signal<IssuedCredentials | null>(null);

  readonly kpis = computed(() => {
    const list = this.users();
    return {
      activos: list.filter((u) => u.estado === 'ACTIVO').length,
      bloqueados: list.filter((u) => u.estado === 'BLOQUEADO').length,
      pendientes: list.filter((u) => u.requiereCambioPassword).length,
      personalizados: this.roles().filter((r) => !r.sistema).length,
    };
  });

  // Usuario
  readonly userOpen = signal(false);
  readonly editingUser = signal<AdminUser | null>(null);
  readonly userRoles = signal<Set<number>>(new Set());
  readonly showPassword = signal(false);
  readonly userForm = this.fb.group({
    tipoDocumento: this.fb.control('CC'),
    numeroDocumento: this.fb.control('', [Validators.required, Validators.maxLength(40)]),
    nombres: this.fb.control('', [Validators.required, Validators.maxLength(120)]),
    apellidos: this.fb.control('', Validators.maxLength(120)),
    telefono: this.fb.control('', Validators.maxLength(40)),
    nombreUsuario: this.fb.control('', [Validators.required, Validators.minLength(3), Validators.maxLength(80)]),
    correo: this.fb.control('', [Validators.required, Validators.email, Validators.maxLength(180)]),
    passwordTemporal: this.fb.control(''),
    alcanceTipo: this.fb.control<ScopeInput['tipo']>('EMPRESA'),
    proyectoId: this.fb.control<number | null>(null),
  });

  // Estado
  readonly statusTarget = signal<{ user: AdminUser; estado: AdminUser['estado'] } | null>(null);
  readonly statusReason = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(300)] });

  // Rol
  readonly roleOpen = signal(false);
  readonly editingRole = signal<AdminRole | null>(null);
  readonly viewingRole = signal<AdminRole | null>(null);
  readonly rolePermissions = signal<Set<number>>(new Set());
  readonly roleForm = this.fb.group({
    codigo: this.fb.control('', [Validators.required, Validators.maxLength(60)]),
    nombre: this.fb.control('', [Validators.required, Validators.maxLength(100)]),
    descripcion: this.fb.control('', Validators.maxLength(500)),
    activo: this.fb.control(true),
  });

  readonly permissionGroups = computed<PermissionGroup[]>(() => {
    const groups = new Map<string, PermissionGroup['items']>();
    for (const p of this.permissions()) {
      const list = groups.get(p.modulo) ?? [];
      list.push({ ...p, owned: this.mine.has(p.codigo) });
      groups.set(p.modulo, list);
    }
    return [...groups.entries()].map(([module, items]) => ({ module, items }));
  });

  constructor() {
    effect(() => {
      const text = this.text();
      const status = this.status();
      untracked(() => this.loadUsers(text, status));
    });
    this.loadRoles();
    if (this.canSeeRoles) {
      this.security.permissions().pipe(catchError(() => of([] as PermissionItem[]))).subscribe((list) => this.permissions.set(list));
    }
    if (this.canCreateUser || this.canEditUser) {
      this.projectService.list({ size: 100 }).pipe(catchError(() => of(null))).subscribe((page) => this.projects.set(page?.contenido ?? []));
    }
  }

  loadUsers(text = this.text(), status = this.status()): void {
    if (!this.canSeeUsers) {
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.security.users(text, status).subscribe({
      next: (list) => {
        this.users.set(list);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(apiErrorMessage(e, 'No se pudieron cargar los usuarios.'));
        this.loading.set(false);
      },
    });
  }

  loadRoles(): void {
    if (!this.canSeeRoles && !this.canCreateUser && !this.canEditUser) return;
    this.security.roles().pipe(catchError(() => of([] as AdminRole[]))).subscribe((list) => this.roles.set(list));
  }

  /** Un rol se puede asignar o editar solo si todos sus permisos los tiene quien lo hace. */
  delegable(role: AdminRole): boolean {
    return role.permisos.every((code) => this.mine.has(code));
  }

  manageable(user: AdminUser): boolean {
    return user.id !== this.me && !user.roles.includes('SUPER_ADMIN');
  }

  scopeLabel(user: AdminUser): string {
    const scope = user.alcances[0];
    if (!scope) return 'Sin alcance';
    if (scope.tipo === 'PROYECTO') return scope.proyectoNombre ?? 'Un proyecto';
    return SCOPE_LABELS[scope.tipo] ?? scope.tipo;
  }

  roleName(code: string): string {
    return this.roles().find((r) => r.codigo === code)?.nombre ?? code;
  }

  // ---------- Usuarios ----------

  openUser(user: AdminUser | null): void {
    this.editingUser.set(user);
    this.userRoles.set(new Set(user?.rolIds ?? []));
    const scope = user?.alcances[0];
    this.userForm.reset({
      tipoDocumento: user?.tipoDocumento ?? 'CC',
      numeroDocumento: user?.numeroDocumento ?? '',
      nombres: user?.nombres ?? '',
      apellidos: user?.apellidos ?? '',
      telefono: user?.telefono ?? '',
      nombreUsuario: user?.nombreUsuario ?? '',
      correo: user?.correo ?? '',
      passwordTemporal: user ? '' : generateTemporaryPassword(),
      alcanceTipo: (scope?.tipo as ScopeInput['tipo']) ?? 'EMPRESA',
      proyectoId: scope?.proyectoId ?? null,
    });
    if (user) {
      this.userForm.controls.numeroDocumento.disable();
      this.userForm.controls.nombreUsuario.disable();
      this.userForm.controls.tipoDocumento.disable();
    } else {
      this.userForm.controls.numeroDocumento.enable();
      this.userForm.controls.nombreUsuario.enable();
      this.userForm.controls.tipoDocumento.enable();
    }
    this.showPassword.set(false);
    this.userOpen.set(true);
  }

  regeneratePassword(): void {
    this.userForm.controls.passwordTemporal.setValue(generateTemporaryPassword());
  }

  suggestUsername(): void {
    if (this.editingUser() || this.userForm.controls.nombreUsuario.value) return;
    const v = this.userForm.getRawValue();
    const first = v.nombres.trim().split(/\s+/)[0] ?? '';
    const last = v.apellidos.trim().split(/\s+/)[0] ?? '';
    const base = `${first}.${last}`.replace(/^\.|\.$/g, '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9.]/g, '');
    if (base.length >= 3) this.userForm.controls.nombreUsuario.setValue(base);
  }

  toggleUserRole(role: AdminRole): void {
    if (!this.delegable(role)) return;
    const next = new Set(this.userRoles());
    next.has(role.id) ? next.delete(role.id) : next.add(role.id);
    this.userRoles.set(next);
  }

  saveUser(): void {
    if (this.userForm.invalid) return this.userForm.markAllAsTouched();
    const roles = [...this.userRoles()];
    if (!roles.length) {
      this.toast.error('Seleccione al menos un rol.');
      return;
    }
    const v = this.userForm.getRawValue();
    const scope: ScopeInput = { tipo: v.alcanceTipo, proyectoId: v.alcanceTipo === 'PROYECTO' ? v.proyectoId : null };
    if (scope.tipo === 'PROYECTO' && !scope.proyectoId) {
      this.toast.error('Elija el proyecto que podrá ver el usuario.');
      return;
    }
    const editing = this.editingUser();
    const optional = (s: string) => s.trim() || null;
    let request;
    if (editing) {
      const payload: AdminUserUpdateInput = {
        nombres: v.nombres.trim(),
        apellidos: optional(v.apellidos),
        telefono: optional(v.telefono),
        correo: v.correo.trim(),
        rolIds: roles,
        alcances: [scope],
        version: editing.version,
      };
      request = this.security.updateUser(editing.id, payload);
    } else {
      if (!STRONG_TEMPORARY_PASSWORD.test(v.passwordTemporal)) {
        this.regeneratePassword();
      }
      const payload: AdminUserCreateInput = {
        tipoDocumento: v.tipoDocumento,
        numeroDocumento: v.numeroDocumento.trim(),
        nombres: v.nombres.trim(),
        apellidos: optional(v.apellidos),
        telefono: optional(v.telefono),
        nombreUsuario: v.nombreUsuario.trim(),
        correo: v.correo.trim(),
        passwordTemporal: this.userForm.controls.passwordTemporal.value,
        rolIds: roles,
        alcances: [scope],
      };
      request = this.security.createUser(payload);
    }
    this.saving.set(true);
    request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (user) => {
        this.userOpen.set(false);
        if (!editing) {
          this.issued.set({ usuario: user.nombreUsuario, password: this.userForm.controls.passwordTemporal.value, nuevo: true });
        }
        this.toast.success(editing ? 'Usuario actualizado.' : `Usuario ${user.nombreUsuario} creado.`);
        this.loadUsers();
      },
      error: (e) => this.toast.error(apiErrorMessage(e, 'No se pudo guardar el usuario.')),
    });
  }

  askStatus(user: AdminUser, estado: AdminUser['estado']): void {
    this.statusReason.reset('');
    this.statusTarget.set({ user, estado });
  }

  confirmStatus(): void {
    const target = this.statusTarget();
    if (!target || this.statusReason.invalid) return this.statusReason.markAsTouched();
    this.saving.set(true);
    this.security
      .changeUserStatus(target.user, target.estado, this.statusReason.value.trim())
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (updated) => {
          this.statusTarget.set(null);
          this.users.update((list) => list.map((u) => (u.id === updated.id ? updated : u)));
          this.toast.success(target.estado === 'ACTIVO' ? 'Usuario activado.' : 'Acceso del usuario suspendido; sus sesiones se cerraron.');
        },
        error: (e) => this.toast.error(apiErrorMessage(e, 'No se pudo cambiar el estado.')),
      });
  }

  resetCredentials(user: AdminUser): void {
    const password = generateTemporaryPassword();
    this.saving.set(true);
    this.security
      .resetCredentials(user.id, password, 'Reinicio solicitado por el administrador de la empresa')
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          this.issued.set({ usuario: user.nombreUsuario, password, nuevo: false });
          this.toast.success('Contraseña temporal generada; las sesiones anteriores se cerraron.');
          this.loadUsers();
        },
        error: (e) => this.toast.error(apiErrorMessage(e, 'No se pudo reiniciar la contraseña.')),
      });
  }

  copy(value: string): void {
    const clipboard = globalThis.navigator?.clipboard;
    if (!clipboard) {
      this.toast.error('Su navegador no permite copiar automáticamente.');
      return;
    }
    void clipboard.writeText(value).then(
      () => this.toast.success('Copiado al portapapeles.'),
      () => this.toast.error('No se pudo copiar.'),
    );
  }

  // ---------- Roles ----------

  openRole(role: AdminRole | null): void {
    if (role && (role.sistema || role.protegido || !this.canEditRole || !this.delegable(role))) {
      this.viewingRole.set(role);
      return;
    }
    this.editingRole.set(role);
    const ids = this.permissions().filter((p) => role?.permisos.includes(p.codigo)).map((p) => p.id);
    this.rolePermissions.set(new Set(ids));
    this.roleForm.reset({
      codigo: role?.codigo ?? '',
      nombre: role?.nombre ?? '',
      descripcion: role?.descripcion ?? '',
      activo: role?.activo ?? true,
    });
    if (role) this.roleForm.controls.codigo.disable();
    else this.roleForm.controls.codigo.enable();
    this.roleOpen.set(true);
  }

  togglePermission(item: PermissionItem & { owned: boolean }): void {
    if (!item.owned) return;
    const next = new Set(this.rolePermissions());
    next.has(item.id) ? next.delete(item.id) : next.add(item.id);
    this.rolePermissions.set(next);
  }

  groupState(group: PermissionGroup): 'all' | 'some' | 'none' {
    const selected = group.items.filter((i) => this.rolePermissions().has(i.id)).length;
    return selected === 0 ? 'none' : selected === group.items.length ? 'all' : 'some';
  }

  toggleGroup(group: PermissionGroup): void {
    const owned = group.items.filter((i) => i.owned);
    const next = new Set(this.rolePermissions());
    const allOn = owned.every((i) => next.has(i.id));
    owned.forEach((i) => (allOn ? next.delete(i.id) : next.add(i.id)));
    this.rolePermissions.set(next);
  }

  groupCount(group: PermissionGroup): number {
    return group.items.filter((i) => this.rolePermissions().has(i.id)).length;
  }

  rolePermissionGroups(role: AdminRole): { module: string; items: PermissionItem[] }[] {
    return this.permissionGroups()
      .map((g) => ({ module: g.module, items: g.items.filter((i) => role.permisos.includes(i.codigo)) }))
      .filter((g) => g.items.length > 0);
  }

  saveRole(): void {
    if (this.roleForm.invalid) return this.roleForm.markAllAsTouched();
    if (!this.rolePermissions().size) {
      this.toast.error('Seleccione al menos un permiso.');
      return;
    }
    const v = this.roleForm.getRawValue();
    const payload: RoleInput = {
      codigo: v.codigo.trim(),
      nombre: v.nombre.trim(),
      descripcion: v.descripcion.trim() || null,
      activo: v.activo,
      permisoIds: [...this.rolePermissions()],
    };
    const editing = this.editingRole();
    this.saving.set(true);
    (editing ? this.security.updateRole(editing.id, payload) : this.security.createRole(payload))
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          this.roleOpen.set(false);
          this.toast.success(editing ? 'Rol actualizado.' : 'Rol creado.');
          this.loadRoles();
        },
        error: (e) => this.toast.error(apiErrorMessage(e, 'No se pudo guardar el rol.')),
      });
  }
}
