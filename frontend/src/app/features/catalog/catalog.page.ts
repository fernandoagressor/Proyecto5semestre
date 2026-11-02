import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { CatalogCompany, CatalogLot, CatalogProject, CatalogProjectDetail } from '../../core/models/catalog.models';
import { CatalogService } from '../../core/services/catalog.service';
import { apiErrorMessage, NX_UI } from '../../shared/ui';

/** Catálogo público de proyectos (vista "Catálogo-Proyectos"): sin sesión, para compartir el enlace. */
@Component({
  selector: 'app-catalog-page',
  imports: [...NX_UI, NgTemplateOutlet, ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './catalog.page.html',
  styleUrl: './catalog.page.scss',
})
export class CatalogPage {
  private readonly catalog = inject(CatalogService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  readonly code = signal('');
  readonly company = signal<CatalogCompany | null>(null);
  readonly detail = signal<CatalogProjectDetail | null>(null);
  readonly loading = signal(true);
  readonly detailLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly city = signal('');
  readonly photo = signal(0);
  readonly sending = signal(false);
  readonly sent = signal(false);
  readonly contactError = signal<string | null>(null);
  readonly selectedLot = signal<CatalogLot | null>(null);

  readonly maxPrice = new FormControl<number | null>(null);
  readonly minArea = new FormControl<number | null>(null);
  readonly priceLimit = signal<number | null>(null);
  readonly areaLimit = signal<number | null>(null);

  readonly cities = computed(() => [...new Set((this.company()?.proyectos ?? []).map((p) => p.ciudad))].sort());
  readonly projects = computed(() => {
    const city = this.city();
    return (this.company()?.proyectos ?? []).filter((p) => !city || p.ciudad === city);
  });
  readonly lots = computed(() => {
    const price = this.priceLimit();
    const area = this.areaLimit();
    return (this.detail()?.lotes ?? []).filter((l) => (!price || l.valor <= price) && (!area || l.areaM2 >= area));
  });
  readonly photos = computed(() => {
    const d = this.detail();
    if (!d) return [];
    const list = d.imagenes.map((i) => ({ url: i.url, titulo: i.titulo }));
    if (d.proyecto.imagenUrl && !list.some((i) => i.url === d.proyecto.imagenUrl)) {
      list.unshift({ url: d.proyecto.imagenUrl, titulo: d.proyecto.nombre });
    }
    return list;
  });

  readonly form = this.fb.group({
    nombres: this.fb.control('', [Validators.required, Validators.maxLength(160)]),
    telefono: this.fb.control('', [Validators.maxLength(40), Validators.pattern(/^[0-9+()\s-]*$/)]),
    correo: this.fb.control('', [Validators.email, Validators.maxLength(180)]),
    mensaje: this.fb.control('', Validators.maxLength(1000)),
    aceptaTratamiento: this.fb.control(false, Validators.requiredTrue),
    sitioWeb: this.fb.control(''),
  });

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const code = params.get('empresa') ?? '';
      const projectId = Number(params.get('proyecto')) || null;
      if (code !== this.code()) {
        this.code.set(code);
        this.loadCompany();
      }
      if (projectId) this.loadProject(projectId);
      else this.detail.set(null);
    });
  }

  loadCompany(): void {
    this.loading.set(true);
    this.error.set(null);
    this.catalog
      .company(this.code())
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (c) => {
          this.company.set(c);
          document.title = `${c.nombre} · Proyectos`;
        },
        error: (e) => this.error.set(e?.status === 404 ? 'Este catálogo no existe o no está publicado.' : apiErrorMessage(e, 'No se pudo cargar el catálogo.')),
      });
  }

  open(project: CatalogProject): void {
    void this.router.navigate(['/catalogo', this.code(), 'proyecto', project.id]);
  }

  back(): void {
    void this.router.navigate(['/catalogo', this.code()]);
  }

  applyFilters(): void {
    this.priceLimit.set(this.maxPrice.value || null);
    this.areaLimit.set(this.minArea.value || null);
  }

  chooseLot(lot: CatalogLot): void {
    this.selectedLot.set(lot);
    const current = this.form.controls.mensaje.value;
    if (!current) this.form.controls.mensaje.setValue(`Me interesa el lote ${lot.codigo} (${lot.areaM2} m²).`);
    document.getElementById('contacto')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  send(): void {
    this.contactError.set(null);
    if (this.form.invalid) return this.form.markAllAsTouched();
    const v = this.form.getRawValue();
    if (!v.telefono.trim() && !v.correo.trim()) {
      this.contactError.set('Déjenos un teléfono o un correo para responderle.');
      return;
    }
    this.sending.set(true);
    this.catalog
      .contact(this.code(), {
        proyectoId: this.detail()?.proyecto.id ?? null,
        nombres: v.nombres.trim(),
        telefono: v.telefono.trim() || null,
        correo: v.correo.trim() || null,
        mensaje: v.mensaje.trim() || null,
        aceptaTratamiento: v.aceptaTratamiento,
        sitioWeb: v.sitioWeb,
      })
      .pipe(finalize(() => this.sending.set(false)))
      .subscribe({
        next: () => this.sent.set(true),
        error: (e) => this.contactError.set(apiErrorMessage(e, 'No pudimos enviar su mensaje. Intente de nuevo.')),
      });
  }

  private loadProject(id: number): void {
    this.detailLoading.set(true);
    this.photo.set(0);
    this.sent.set(false);
    this.selectedLot.set(null);
    this.catalog
      .project(this.code(), id)
      .pipe(finalize(() => this.detailLoading.set(false)))
      .subscribe({
        next: (d) => {
          this.detail.set(d);
          globalThis.scrollTo?.({ top: 0 });
        },
        error: () => {
          this.detail.set(null);
          this.back();
        },
      });
  }
}
