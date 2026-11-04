import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'wwt-page-header',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './page-header.component.html',
  styleUrl: './page-header.component.scss'
})
export class PageHeaderComponent {
  @Input() title = '';
  @Input() subtitle = '';
  @Input() headingLevel: 1 | 2 = 2;
  @Input() headingId: string | null = null;
  /** @deprecated plus affiché — conservé pour compatibilité avec les pages existantes */
  @Input() breadcrumb = '';
}
