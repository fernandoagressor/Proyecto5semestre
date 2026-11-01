import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { AuthService } from '../../core/services/auth.service';
import { KnowledgeArticle, KnowledgeCategory, SupportService } from '../../core/services/support.service';
import { apiErrorMessage, NX_UI, ToastService } from '../../shared/ui';

/**
 * Base de conocimiento: buscar artículos y, con permiso, crearlos, editarlos, publicarlos o
 * archivarlos. En modo selector (`picker`) solo muestra publicados y emite el elegido.
 */
@Component({
  selector: 'app-knowledge-tab',
  imports: [...NX_UI, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './service.sections.scss',
  template: `
    <div class="nx-section-head">
      <span class="nx-search kb-search"><app-icon name="search" /><input class="nx-input" type="search" placeholder="Buscar en la base de conocimiento" [ngModel]="text()" (ngModelChange)="search($event)" /></span>
      <div class="nx-row">
        <select class="nx-select nx-select--sm" [ngModel]="categoryId()" (ngModelChange)="categoryId.set($event); load()" aria-label="Categoría">
          <option [ngValue]="null">Todas las categorías</option>
          @for (c of categories(); track c.id) { <option [ngValue]="c.id">{{ c.nombre }}</option> }
        </select>
        @if (canEdit && !picker()) {
          <button type="button" class="nx-btn nx-btn--sm" (click)="categoryOpen.set(true)">Categoría</button>
          <button type="button" class="nx-btn nx-btn--sm nx-btn--primary" (click)="edit(null)"><app-icon name="plus" /> Artículo</button>
        }
      </div>
    </div>
    <nx-state [loading]="loading()" [error]="error()" [empty]="visible().length === 0" (retry)="load()" emptyIcon="book" emptyTitle="Sin artículos" emptyText="Pruebe con otras palabras o cree el primer artículo.">
      <div class="nx-grid nx-grid--cards">
        @for (a of visible(); track a.id) {
          <article class="nx-card kb-card" [class.nx-card--interactive]="picker()" (click)="picker() && picked.emit(a)">
            <span class="nx-eyebrow">{{ categoryName(a.categoriaId) }}</span>
            <b>{{ a.titulo }}</b>
            <p>{{ excerpt(a) }}</p>
            <div class="nx-row nx-row--between">
              <span class="nx-row">
                <nx-badge [status]="a.estado" />
                @if (a.visibleClientes) { <nx-badge tone="info">Visible al cliente</nx-badge> }
              </span>
              @if (canEdit && !picker()) {
                <span class="nx-row">
                  <button type="button" class="nx-btn nx-btn--xs" (click)="edit(a)">Editar</button>
                  @if (a.estado !== 'PUBLICADO') { <button type="button" class="nx-btn nx-btn--xs nx-btn--primary" (click)="act(a, 'publicar')" [disabled]="busy()">Publicar</button> }
                  @if (a.estado === 'PUBLICADO') { <button type="button" class="nx-btn nx-btn--xs nx-btn--ghost" (click)="act(a, 'archivar')" [disabled]="busy()">Archivar</button> }
                </span>
              }
            </div>
          </article>
        }
      </div>
    </nx-state>

    <nx-drawer [open]="editOpen()" (openChange)="editOpen.set($event)" width="680px" [title]="editing() ? 'Editar artículo' : 'Nuevo artículo'" [dismissible]="!busy()">
      <div class="nx-stack">
        <label class="nx-field"><span>Título</span><input class="nx-input" maxlength="220" [(ngModel)]="title" /></label>
        <label class="nx-field">
          <span>Categoría</span>
          <select class="nx-select" [(ngModel)]="formCategory"><option [ngValue]="null">Seleccione…</option>@for (c of categories(); track c.id) { <option [ngValue]="c.id">{{ c.nombre }}</option> }</select>
        </label>
        <label class="nx-field"><span>Contenido</span><textarea class="nx-textarea content" [(ngModel)]="content"></textarea></label>
        <label class="nx-check"><input type="checkbox" [(ngModel)]="visibleClients" /> Visible para los clientes en el portal (cuando esté publicado)</label>
      </div>
      <ng-container drawer-footer>
        <button type="button" class="nx-btn" (click)="editOpen.set(false)" [disabled]="busy()">Cancelar</button>
        <button type="button" class="nx-btn nx-btn--primary" (click)="save()" [disabled]="busy() || !title.trim() || !content.trim() || !formCategory">Guardar</button>
      </ng-container>
    </nx-drawer>

    <nx-drawer [open]="categoryOpen()" (openChange)="categoryOpen.set($event)" variant="dialog" title="Nueva categoría" [dismissible]="!busy()">
      <div class="nx-stack">
        <label class="nx-field"><span>Nombre</span><input class="nx-input" maxlength="120" [(ngModel)]="categoryName_" /></label>
        <label class="nx-field"><span>Descripción</span><input class="nx-input" maxlength="500" [(ngModel)]="categoryDesc" /></label>
      </div>
      <ng-container drawer-footer>
        <button type="button" class="nx-btn" (click)="categoryOpen.set(false)" [disabled]="busy()">Cancelar</button>
        <button type="button" class="nx-btn nx-btn--primary" (click)="saveCategory()" [disabled]="busy() || !categoryName_.trim()">Crear</button>
      </ng-container>
    </nx-drawer>
  `,
})
export class KnowledgeTabComponent {
  private readonly api = inject(SupportService);
  private readonly toast = inject(ToastService);

  /** Modo selector: solo publicados, sin edición; al tocar un artículo se emite `picked`. */
  readonly picker = input(false);
  readonly picked = output<KnowledgeArticle>();

  readonly canEdit = inject(AuthService).hasAuthority('BASE_CONOCIMIENTO_EDITAR');
  readonly categories = signal<KnowledgeCategory[]>([]);
  readonly articles = signal<KnowledgeArticle[]>([]);
  readonly categoryId = signal<number | null>(null);
  readonly text = signal('');
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  readonly editOpen = signal(false);
  readonly categoryOpen = signal(false);
  readonly editing = signal<KnowledgeArticle | null>(null);
  readonly visible = computed(() => (this.picker() ? this.articles().filter((a) => a.estado === 'PUBLICADO') : this.articles()));
  private readonly search$ = new Subject<string>();

  title = '';
  content = '';
  formCategory: number | null = null;
  visibleClients = false;
  categoryName_ = '';
  categoryDesc = '';

  constructor() {
    this.api.categories().subscribe({ next: (c) => this.categories.set(c), error: () => this.categories.set([]) });
    this.search$.pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed()).subscribe(() => this.load());
    this.load();
  }

  search(value: string): void {
    this.text.set(value);
    this.search$.next(value.trim());
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.articles({ categoriaId: this.categoryId(), texto: this.text().trim() }).subscribe({
      next: (page) => {
        this.articles.set(page.contenido);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(apiErrorMessage(e, 'No se pudo cargar la base de conocimiento.'));
        this.loading.set(false);
      },
    });
  }

  categoryName(id: number): string {
    return this.categories().find((c) => c.id === id)?.nombre ?? 'General';
  }

  excerpt(a: KnowledgeArticle): string {
    const plain = a.contenido.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
    return plain.length > 160 ? plain.slice(0, 157) + '…' : plain;
  }

  edit(a: KnowledgeArticle | null): void {
    this.editing.set(a);
    this.title = a?.titulo ?? '';
    this.content = a?.contenido ?? '';
    this.formCategory = a?.categoriaId ?? this.categoryId() ?? this.categories()[0]?.id ?? null;
    this.visibleClients = a?.visibleClientes ?? false;
    this.editOpen.set(true);
  }

  save(): void {
    if (!this.formCategory) return;
    const a = this.editing();
    this.busy.set(true);
    this.api
      .saveArticle(a?.id ?? null, { categoriaId: this.formCategory, titulo: this.title.trim(), slug: a?.slug ?? null, contenido: this.content.trim(), visibleClientes: this.visibleClients })
      .subscribe({
        next: () => this.done(a ? 'Artículo actualizado.' : 'Artículo creado en borrador.', () => this.editOpen.set(false)),
        error: (e) => this.failed(e),
      });
  }

  act(a: KnowledgeArticle, action: 'publicar' | 'archivar'): void {
    this.busy.set(true);
    this.api.articleAction(a.id, action).subscribe({
      next: () => this.done(action === 'publicar' ? 'Artículo publicado.' : 'Artículo archivado.'),
      error: (e) => this.failed(e),
    });
  }

  saveCategory(): void {
    this.busy.set(true);
    this.api.createCategory(this.categoryName_.trim(), this.categoryDesc.trim() || null, this.categories().length + 1).subscribe({
      next: (c) => {
        this.categories.update((list) => [...list, c]);
        this.categoryName_ = '';
        this.categoryDesc = '';
        this.done('Categoría creada.', () => this.categoryOpen.set(false));
      },
      error: (e) => this.failed(e),
    });
  }

  private done(message: string, after?: () => void): void {
    this.busy.set(false);
    this.toast.success(message);
    after?.();
    this.load();
  }

  private failed(e: unknown): void {
    this.busy.set(false);
    this.toast.error(apiErrorMessage(e));
  }
}
