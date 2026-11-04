import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'wwt-support-technique',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './support-technique.component.html',
  styleUrl: './support-technique.component.scss'
})
export class SupportTechniqueComponent {
  private readonly auth = inject(AuthService);

  protected readonly isAuthenticated = computed(() => this.auth.authenticated());
  protected readonly interventionLink = computed(() =>
    this.isAuthenticated() ? '/client/maintenance/new' : '/contact'
  );

  protected readonly whatsappUrl = 'https://wa.me/212676886836?text=Bonjour%20WWTRADING%2C%20j%27ai%20besoin%20d%27une%20assistance%20technique%20urgente%20pour%20mon%20copieur.';
}
