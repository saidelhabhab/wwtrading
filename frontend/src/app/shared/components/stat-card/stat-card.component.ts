import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'wwt-stat-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './stat-card.component.html',
  styleUrl: './stat-card.component.scss'
})
export class StatCardComponent {
  @Input() title = '';
  @Input() value = '';
  @Input() icon = '•';
  @Input() description = '';
  @Input() trend = '';
  @Input() trendDirection: 'up' | 'down' | 'neutral' = 'neutral';
  @Input() status: 'default' | 'success' | 'warning' | 'danger' = 'default';
  @Input() loading = false;
}
