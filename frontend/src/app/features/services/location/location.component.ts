import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'wwt-location',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './location.component.html',
  styleUrl: './location.component.scss'
})
export class LocationComponent {
  protected readonly whatsappUrl = 'https://wa.me/212676886836?text=Bonjour%20WWTRADING%2C%20je%20souhaite%20une%20proposition%20de%20location%20de%20photocopieur.';
}
