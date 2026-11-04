import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'wwt-vente-machines',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './vente-machines.component.html',
  styleUrl: './vente-machines.component.scss'
})
export class VenteMachinesComponent {
  protected readonly whatsappUrl = 'https://wa.me/212676886836?text=Bonjour%20WWTRADING%2C%20je%20souhaite%20des%20informations%20sur%20l%27achat%20de%20machines%20d%27impression.';
}
