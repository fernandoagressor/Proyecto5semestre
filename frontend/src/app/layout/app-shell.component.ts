import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, filter, finalize, of } from 'rxjs';

import { NAVIGATION_ITEMS } from '../core/navigation/navigation.config';
import { CompanyModuleCode } from '../core/models/company-module.models';
import { AuthService } from '../core/services/auth.service';
import { CompanyService } from '../core/services/company.service';
import { AppNotification, NotificationService } from '../core/services/notification.service';
import { ThemeService } from '../core/services/theme.service';
import { AppIconComponent } from '../shared/components/app-icon.component';
import { BrandMarkComponent } from '../shared/components/brand-mark.component';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AppIconComponent, BrandMarkComponent],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShellComponent {
  readonly auth = inject(AuthService);
  readonly theme = inject(ThemeService);
  private readonly companyService = inject(CompanyService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly mobileMenuOpen = signal(false);
  readonly profileOpen = signal(false);
  readonly notificationOpen = signal(false);
  readonly companyName = signal('Nexora Core');
  readonly companyModules = signal<readonly CompanyModuleCode[] | null>(null);
  readonly unreadNotifications = signal(0);
  readonly recentNotifications = signal<AppNotification[]>([]);
  readonly notificationsLoading = signal(false);
  readonly pageTitle = signal('Dashboard');
  readonly loggingOut = signal(false);
  readonly navigation = computed(() =>
    NAVIGATION_ITEMS.filter((item) => {
      if (!this.auth.hasAny(item.permissions)) return false;
      const modules = this.companyModules();
      return !item.module || modules === null || modules.includes(item.module);
    }),
  );

  constructor() {
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => {
        this.mobileMenuOpen.set(false);
        this.profileOpen.set(false);
        this.notificationOpen.set(false);
        this.updatePageTitle();
      });

    effect((onCleanup) => {
      const companyId = this.auth.user()?.empresaId;
      if (!companyId) {
        this.companyName.set('Nexora Core');
        this.companyModules.set(null);
        return;
      }
      const subscription = this.companyService
        .current()
        .pipe(catchError(() => of(null)))
        .subscribe((company) => {
          this.companyName.set(company?.nombre ?? 'Nexora Core');
          this.companyModules.set(company?.modulos ?? null);
        });
      onCleanup(() => subscription.unsubscribe());
    });

    this.notifications
      .unreadSummary()
      .pipe(catchError(() => of({ noLeidas: 0 })), takeUntilDestroyed())
      .subscribe((summary) => this.unreadNotifications.set(summary.noLeidas));
  }

  logout(): void {
    if (this.loggingOut()) return;
    this.loggingOut.set(true);
    const request = this.auth.logoutRequest();
    if (!request) {
      this.finishLogout();
      return;
    }
    request
      .pipe(finalize(() => this.finishLogout()), takeUntilDestroyed(this.destroyRef))
      .subscribe({ error: () => undefined });
  }

  toggleNotifications(): void {
    this.profileOpen.set(false);
    this.notificationOpen.update((open) => !open);
    if (!this.notificationOpen() || this.recentNotifications().length > 0) return;

    this.notificationsLoading.set(true);
    this.notifications
      .unreadList()
      .pipe(
        catchError(() => of(null)),
        finalize(() => this.notificationsLoading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((page) => this.recentNotifications.set(page?.contenido ?? []));
  }

  private finishLogout(): void {
    this.auth.clearSession();
    this.loggingOut.set(false);
    void this.router.navigate(['/login']);
  }

  private updatePageTitle(): void {
    let activeRoute = this.router.routerState.snapshot.root;
    while (activeRoute.firstChild) activeRoute = activeRoute.firstChild;
    this.pageTitle.set((activeRoute.data?.['title'] as string | undefined) ?? 'Nexora Core');
  }
}
