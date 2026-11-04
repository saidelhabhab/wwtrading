import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'wwt-pieces-rechange',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pieces-rechange.component.html',
  styleUrl: './pieces-rechange.component.scss'
})
export class PiecesRechangeComponent {
  protected readonly whatsappUrl = 'https://wa.me/212676886836?text=Bonjour%20WWTRADING%2C%20je%20recherche%20une%20pi%C3%A8ce%20de%20rechange%20pour%20mon%20copieur.';
}
