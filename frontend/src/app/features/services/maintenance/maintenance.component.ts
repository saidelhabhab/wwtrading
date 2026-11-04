import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'wwt-maintenance',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './maintenance.component.html',
  styleUrl: './maintenance.component.scss'
})
export class MaintenanceComponent {
  private readonly auth = inject(AuthService);
  protected readonly isAuthenticated = computed(() => this.auth.authenticated());
  protected readonly interventionLink = computed(() =>
    this.isAuthenticated() ? '/client/maintenance/new' : '/contact'
  );
  protected readonly whatsappUrl = 'https://wa.me/212676886836?text=Bonjour%20WWTRADING%2C%20je%20souhaite%20demander%20une%20intervention%20technique%20urgente.';
}
