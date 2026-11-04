import { ChangeDetectionStrategy, Component, inject, Input, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, finalize, of, switchMap } from 'rxjs';

import { AddressApiService } from './address-api.service';
import { Address, AddressRequest } from './address.models';

@Component({
  selector: 'wwt-addresses',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  host: { '[class.embedded]': 'embedded' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './address.component.html',
  styles: [`
    :host { display: block; background: #f6f4f2; }
    :host(.embedded) { background: transparent; }
    :host(.embedded) .page { min-height: auto; padding: 0; }
    .page { max-width: 1120px; min-height: 65vh; margin: 0 auto; padding: clamp(2.5rem, 7vw, 6rem) clamp(1rem, 5vw, 4rem); }
    header { display: flex; align-items: end; justify-content: space-between; gap: 1.5rem; margin-bottom: 2rem; }
    .eyebrow { margin: 0 0 .55rem; color: #af0f50; font-size: .76rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    h1 { margin: 0; color: #1a1a18; font-size: clamp(2.3rem, 6vw, 4.25rem); line-height: 1; }
    header p:last-child { margin-bottom: 0; color: #64615e; }
    button { padding: .7rem 1rem; border: 1px solid #af0f50; border-radius: 999px; background: #fff; color: #8f123f; font: inherit; font-weight: 750; cursor: pointer; }
    button.primary { background: #af0f50; color: #fff; }
    button:disabled { cursor: not-allowed; opacity: .55; }
    button:focus-visible, input:focus-visible { outline: 3px solid #d02870; outline-offset: 3px; }
    .feedback { margin-bottom: 1rem; padding: .85rem 1rem; border-left: 4px solid #af0f50; background: #fff; color: #651032; }
    .feedback.success { border-color: #287a50; color: #185c3a; }
    .state { display: grid; justify-items: start; gap: .8rem; padding: 1.5rem; border: 1px solid rgb(26 26 24 / 12%); background: #fff; }
    .skeleton { display: grid; gap: .8rem; }
    .skeleton span { display: block; height: 8rem; background: #e8e5e3; }
    .cards { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }
    .card { position: relative; padding: 1.4rem; border: 1px solid rgb(26 26 24 / 12%); border-top: 4px solid #1a1a18; background: #fff; box-shadow: 0 .8rem 2rem rgb(26 26 24 / 6%); }
    .card.default { border-top-color: #af0f50; }
    .badge { display: inline-flex; margin-bottom: .9rem; padding: .3rem .55rem; border-radius: 999px; background: #f7dce8; color: #8f123f; font-size: .72rem; font-weight: 800; }
    .card h2 { margin: 0 0 .3rem; color: #1a1a18; }
    address { color: #575653; font-style: normal; line-height: 1.65; }
    .card-actions { display: flex; flex-wrap: wrap; gap: .55rem; margin-top: 1.1rem; }
    .form-panel { margin-bottom: 1.5rem; padding: clamp(1.25rem, 4vw, 2rem); border-top: 4px solid #af0f50; background: #fff; box-shadow: 0 1rem 2.5rem rgb(26 26 24 / 8%); }
    .form-panel h2 { margin-top: 0; }
    form { display: grid; gap: 1rem; }
    .form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }
    .field { display: grid; gap: .4rem; }
    .full { grid-column: 1 / -1; }
    label { color: #292825; font-weight: 750; }
    input { box-sizing: border-box; width: 100%; padding: .8rem .9rem; border: 1px solid #aaa6a1; border-radius: .35rem; font: inherit; }
    .field-error { margin: 0; color: #92113c; font-size: .84rem; }
    .form-actions { display: flex; justify-content: flex-end; gap: .7rem; margin-top: .5rem; }
    .return-link { display: inline-flex; margin-bottom: 1rem; color: #8f123f; font-weight: 750; text-underline-offset: .2em; }
    @media (max-width: 700px) { header { align-items: start; flex-direction: column; } .cards, .form-grid { grid-template-columns: 1fr; } .full { grid-column: auto; } }
  `]
})
export class AddressComponent implements OnInit {
  @Input() embedded = false;
  private readonly api = inject(AddressApiService);
  private readonly route = inject(ActivatedRoute);

  protected readonly addresses = signal<readonly Address[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly formOpen = signal(false);
  protected readonly editingId = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly pendingId = signal<string | null>(null);
  protected readonly returnToCheckout = this.route.snapshot.queryParamMap.get('returnUrl') === '/checkout';

  protected readonly form = new FormGroup({
    firstName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)] }),
    lastName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)] }),
    company: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(200)] }),
    phone: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(32)] }),
    addressLine1: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(255)] }),
    addressLine2: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(255)] }),
    city: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(120)] }),
    postalCode: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(32)] }),
    country: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)] })
  });

  ngOnInit(): void { this.load(); }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.getAddresses().pipe(
      catchError(() => { this.error.set('Impossible de charger vos adresses pour le moment.'); return of(null); }),
      finalize(() => this.loading.set(false))
    ).subscribe((addresses) => { if (addresses) this.addresses.set(addresses); });
  }

  protected openCreate(): void {
    this.editingId.set(null);
    this.form.reset();
    this.formOpen.set(true);
    this.success.set(null);
    this.error.set(null);
  }

  protected openEdit(address: Address): void {
    this.editingId.set(address.id);
    this.form.setValue({
      firstName: address.firstName, lastName: address.lastName,
      company: address.company ?? '', phone: address.phone ?? '',
      addressLine1: address.addressLine1, addressLine2: address.addressLine2 ?? '',
      city: address.city, postalCode: address.postalCode ?? '', country: address.country
    });
    this.formOpen.set(true);
    this.success.set(null);
    this.error.set(null);
  }

  protected cancelForm(): void { this.formOpen.set(false); this.editingId.set(null); this.form.reset(); }

  protected submit(): void {
    if (this.form.invalid || this.saving()) { this.form.markAllAsTouched(); return; }
    const request = this.toRequest();
    const id = this.editingId();
    this.saving.set(true);
    this.error.set(null);
    const operation = id ? this.api.updateAddress(id, request) : this.api.createAddress(request);
    operation.pipe(
      switchMap(() => this.api.getAddresses()),
      catchError(() => { this.error.set('L’adresse ne peut pas être enregistrée pour le moment.'); return of(null); }),
      finalize(() => this.saving.set(false))
    ).subscribe((addresses) => {
      if (!addresses) return;
      this.addresses.set(addresses);
      this.success.set(id ? 'Adresse mise à jour.' : 'Adresse ajoutée.');
      this.cancelForm();
    });
  }

  protected deleteAddress(address: Address): void {
    if (this.pendingId() || !window.confirm(`Supprimer l’adresse de ${address.firstName} ${address.lastName} ?`)) return;
    this.pendingId.set(address.id);
    this.error.set(null);
    this.api.deleteAddress(address.id).pipe(
      switchMap(() => this.api.getAddresses()),
      catchError(() => { this.error.set('Impossible de supprimer cette adresse.'); return of(null); }),
      finalize(() => this.pendingId.set(null))
    ).subscribe((addresses) => {
      if (!addresses) return;
      this.addresses.set(addresses);
      this.success.set('Adresse supprimée.');
    });
  }

  protected makeDefault(address: Address): void {
    if (address.defaultAddress || this.pendingId()) return;
    this.pendingId.set(address.id);
    this.error.set(null);
    this.api.setDefaultAddress(address.id).pipe(
      switchMap(() => this.api.getAddresses()),
      catchError(() => { this.error.set('Impossible de définir cette adresse par défaut.'); return of(null); }),
      finalize(() => this.pendingId.set(null))
    ).subscribe((addresses) => {
      if (!addresses) return;
      this.addresses.set(addresses);
      this.success.set('Adresse par défaut mise à jour.');
    });
  }

  protected showError(name: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || control.dirty);
  }

  private toRequest(): AddressRequest {
    const value = this.form.getRawValue();
    const optional = (field: string): string | null => field.trim() || null;
    return {
      firstName: value.firstName.trim(), lastName: value.lastName.trim(),
      company: optional(value.company), phone: optional(value.phone),
      addressLine1: value.addressLine1.trim(), addressLine2: optional(value.addressLine2),
      city: value.city.trim(), postalCode: optional(value.postalCode), country: value.country.trim()
    };
  }
}
