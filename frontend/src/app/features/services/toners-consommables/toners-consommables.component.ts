import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'wwt-toners-consommables',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './toners-consommables.component.html',
  styleUrl: './toners-consommables.component.scss'
})
export class TonersConsommablesComponent {
  protected readonly whatsappUrl = 'https://wa.me/212676886836?text=Bonjour%20WWTRADING%2C%20je%20recherche%20des%20toners%20ou%20consommables%20pour%20mes%20machines.';
}
