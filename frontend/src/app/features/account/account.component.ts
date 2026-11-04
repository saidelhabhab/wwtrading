import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { AuthApiService, ChangePasswordPayload } from '../../core/auth/auth-api.service';
import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { AddressComponent } from '../addresses/address.component';

interface UserProfile {
  readonly id: string;
  readonly email: string;
  readonly firstName: string | null;
  readonly lastName: string | null;
  readonly locale: string | null;
  readonly enabled: boolean;
  readonly roles: readonly string[];
  readonly phone?: string | null;
}

interface UpdateUserProfileRequest {
  readonly firstName: string | null;
  readonly lastName: string | null;
  readonly locale: string | null;
}

const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&_\-#.+='":;><,{}~`|\[\]\\\/]).{12,}$/;

@Component({
  selector: 'wwt-account',
  standalone: true,
  imports: [ReactiveFormsModule, PageHeaderComponent, AddressComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './account.component.html',
  styleUrl: './account.component.scss'
})
export class AccountComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly authApi = inject(AuthApiService);
  private readonly config = inject(RUNTIME_CONFIG);

  protected readonly profile = signal<UserProfile | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly editing = signal(false);
  protected readonly saving = signal(false);
  protected readonly isClient = computed(() => this.auth.roles().includes('CLIENT'));

  // Password change state
  protected readonly showChangePassword = signal(false);
  protected readonly changingPassword = signal(false);
  protected readonly passwordSuccess = signal<string | null>(null);
  protected readonly passwordError = signal<string | null>(null);
  protected readonly showCurrentPassword = signal(false);
  protected readonly showNewPassword = signal(false);
  protected readonly showConfirmPassword = signal(false);

  protected readonly form = new FormGroup({
    firstName: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(100)] }),
    lastName: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(100)] }),
    locale: new FormControl('', {
      nonNullable: true,
      validators: [Validators.pattern(/^[a-zA-Z]{2,3}([_-][a-zA-Z]{2})?$/)]
    })
  });

  protected readonly passwordForm = new FormGroup({
    currentPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    newPassword: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(PASSWORD_REGEX)]
    }),
    confirmPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  }, { validators: passwordMatchValidator });

  // Password checklist signals
  protected readonly newPasswordValue = signal('');

  constructor() {
    this.passwordForm.controls.newPassword.valueChanges.subscribe((val) => {
      this.newPasswordValue.set(val || '');
    });
  }

  protected readonly pwdHasMinLength = computed(() => this.newPasswordValue().length >= 12);
  protected readonly pwdHasUpper = computed(() => /[A-Z]/.test(this.newPasswordValue()));
  protected readonly pwdHasLower = computed(() => /[a-z]/.test(this.newPasswordValue()));
  protected readonly pwdHasNumber = computed(() => /\d/.test(this.newPasswordValue()));
  protected readonly pwdHasSpecial = computed(() => /[@$!%*?&_\-#.+='":;><,{}~`|\[\]\\\/]/.test(this.newPasswordValue()));

  ngOnInit(): void { this.loadProfile(); }

  protected loadProfile(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<UserProfile>(`${this.config.apiBaseUrl}/api/v1/users/me`).pipe(
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: (profile) => {
        this.profile.set(profile);
        this.populateForm(profile);
      },
      error: (failure: unknown) => this.handleError(failure, 'Le profil ne peut pas être chargé pour le moment.')
    });
  }

  protected startEditing(): void {
    const profile = this.profile();
    if (!profile || !this.isClient()) return;
    this.populateForm(profile);
    this.error.set(null);
    this.success.set(null);
    this.editing.set(true);
  }

  protected cancelEditing(): void {
    const profile = this.profile();
    if (profile) this.populateForm(profile);
    this.editing.set(false);
  }

  protected saveProfile(): void {
    if (!this.isClient() || this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const request: UpdateUserProfileRequest = {
      firstName: this.optional(value.firstName),
      lastName: this.optional(value.lastName),
      locale: this.optional(value.locale)
    };
    this.saving.set(true);
    this.error.set(null);
    this.success.set(null);
    this.http.patch<UserProfile>(`${this.config.apiBaseUrl}/api/v1/users/me`, request).pipe(
      finalize(() => this.saving.set(false))
    ).subscribe({
      next: (profile) => {
        this.profile.set(profile);
        this.populateForm(profile);
        this.editing.set(false);
        this.success.set('Votre profil a été mis à jour.');
      },
      error: (failure: unknown) => this.handleError(failure, 'Votre profil ne peut pas être enregistré pour le moment.')
    });
  }

  protected toggleChangePassword(): void {
    this.showChangePassword.update((v) => !v);
    this.passwordError.set(null);
    this.passwordSuccess.set(null);
    this.passwordForm.reset();
  }

  protected submitChangePassword(): void {
    if (this.passwordForm.invalid || this.changingPassword()) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    const value = this.passwordForm.getRawValue();
    const payload: ChangePasswordPayload = {
      currentPassword: value.currentPassword,
      newPassword: value.newPassword,
      confirmPassword: value.confirmPassword
    };

    this.changingPassword.set(true);
    this.passwordError.set(null);
    this.passwordSuccess.set(null);

    this.authApi.changePassword(payload).pipe(
      finalize(() => this.changingPassword.set(false))
    ).subscribe({
      next: (res) => {
        this.passwordSuccess.set(res.message || 'Votre mot de passe a été modifié avec succès.');
        this.passwordForm.reset();
        setTimeout(() => this.showChangePassword.set(false), 3000);
      },
      error: (err: unknown) => {
        if (err && typeof err === 'object' && 'status' in err) {
          const status = (err as { status: number }).status;
          const errorBody = (err as { error?: { message?: string } }).error;
          if (status === 400 && errorBody?.message) {
            this.passwordError.set(errorBody.message);
            return;
          }
        }
        this.passwordError.set('Impossible de modifier votre mot de passe pour le moment.');
      }
    });
  }

  protected showError(control: keyof typeof this.form.controls): boolean {
    const field = this.form.controls[control];
    return field.invalid && (field.touched || field.dirty);
  }

  protected showPwdError(control: keyof typeof this.passwordForm.controls): boolean {
    const field = this.passwordForm.controls[control];
    return field.invalid && (field.touched || field.dirty);
  }

  protected showPwdMismatchError(): boolean {
    return this.passwordForm.hasError('passwordMismatch') &&
      (this.passwordForm.controls.confirmPassword.touched || this.passwordForm.controls.confirmPassword.dirty);
  }

  private populateForm(profile: UserProfile): void {
    this.form.setValue({
      firstName: profile.firstName ?? '',
      lastName: profile.lastName ?? '',
      locale: profile.locale ?? ''
    });
  }

  private optional(value: string): string | null { return value.trim() || null; }

  private handleError(failure: unknown, fallback: string): void {
    if (failure instanceof HttpErrorResponse && (failure.status === 401 || failure.status === 403)) {
      this.error.set('Votre session a expiré. Veuillez vous reconnecter.');
      void this.auth.logout();
      return;
    }
    if (failure instanceof HttpErrorResponse && failure.status === 400) {
      this.error.set('Certaines informations du profil sont invalides.');
      return;
    }
    this.error.set(fallback);
  }
}

function passwordMatchValidator(group: AbstractControl): ValidationErrors | null {
  const pwd = group.get('newPassword')?.value;
  const confirm = group.get('confirmPassword')?.value;
  return pwd && confirm && pwd !== confirm ? { passwordMismatch: true } : null;
}
