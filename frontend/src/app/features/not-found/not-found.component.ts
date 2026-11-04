import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SeoService } from '../../core/seo/seo.service';

@Component({
  selector: 'wwt-not-found',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="not-found" aria-labelledby="not-found-title">
      <p class="eyebrow">Erreur 404</p>
      <h1 id="not-found-title">Cette page n’existe pas</h1>
      <p>Le contenu recherché a peut-être été déplacé ou l’adresse saisie est incorrecte.</p>
      <nav aria-label="Navigation depuis la page introuvable">
        <a class="primary" routerLink="/">Retour à l’accueil</a>
        <a routerLink="/products">Voir le catalogue</a>
      </nav>
    </main>
  `,
  styles: [`
    :host { display: block; }
    .not-found { width: min(100% - 2rem, 62rem); margin: 0 auto; padding: clamp(5rem, 12vw, 10rem) 0; text-align: center; }
    .eyebrow { margin: 0 0 1rem; color: #af0f50; font-size: .8rem; font-weight: 800; letter-spacing: .14em; text-transform: uppercase; }
    h1 { margin: 0; color: #1a1a18; font-size: clamp(2.2rem, 7vw, 5rem); line-height: 1.05; }
    p:not(.eyebrow) { max-width: 38rem; margin: 1.5rem auto 2rem; color: #625f5c; font-size: 1.05rem; line-height: 1.7; }
    nav { display: flex; flex-wrap: wrap; justify-content: center; gap: .75rem; }
    a { min-height: 44px; padding: .75rem 1.15rem; border: 1px solid #1a1a18; border-radius: .45rem; color: #1a1a18; font-weight: 750; text-decoration: none; }
    a.primary { border-color: #af0f50; background: #af0f50; color: #fff; }
    a:focus-visible { outline: 3px solid rgb(175 15 80 / 30%); outline-offset: 3px; }
  `]
})
export class NotFoundComponent implements OnInit {
  private readonly seo = inject(SeoService);
  ngOnInit(): void { this.seo.setNotFound(); }
}
