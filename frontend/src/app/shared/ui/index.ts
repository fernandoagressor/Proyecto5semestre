// Piezas del sistema visual del rediseño. Las pantallas importan desde aquí:
//   import { NX_UI } from '../../shared/ui';
//   @Component({ imports: [...NX_UI] })
import { AppIconComponent } from '../components/app-icon.component';
import { BadgeComponent } from './badge.component';
import { DrawerComponent } from './drawer.component';
import { EmptyStateComponent } from './empty-state.component';
import { FilterChipsComponent } from './filter-chips.component';
import { CopPipe, FechaPipe, PorcentajePipe } from './format';
import { KpiCardComponent } from './kpi-card.component';
import { PageHeaderComponent } from './page-header.component';
import { TabsComponent } from './tabs.component';

export * from './badge.component';
export * from './drawer.component';
export * from './empty-state.component';
export * from './filter-chips.component';
export * from './format';
export * from './kpi-card.component';
export * from './page-header.component';
export * from './tabs.component';

export const NX_UI = [
  AppIconComponent,
  BadgeComponent,
  DrawerComponent,
  EmptyStateComponent,
  FilterChipsComponent,
  KpiCardComponent,
  PageHeaderComponent,
  TabsComponent,
  CopPipe,
  FechaPipe,
  PorcentajePipe,
] as const;
