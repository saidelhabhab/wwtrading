import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthApiService, RegisterPayload } from '../../core/auth/auth-api.service';

const MOROCCAN_PHONE_REGEX = /^(?:\+212|0)[67]\d{8}$/;
const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&_\-#.+='":;><,{}~`|\[\]\\\/]).{12,}$/;

@Component({
  selector: 'wwt-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss'
})
export class RegisterComponent {
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);

  protected readonly showPassword = signal(false);
  protected readonly showConfirmPassword = signal(false);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);
  readonly lang = signal<'FR' | 'EN'>('FR');

  toggleLanguage(): void {
    this.lang.update((current) => (current === 'FR' ? 'EN' : 'FR'));
  }

  protected readonly form = new FormGroup({
    firstName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100)]
    }),
    lastName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100)]
    }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(320)]
    }),
    phone: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.pattern(MOROCCAN_PHONE_REGEX)
      ]
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.pattern(PASSWORD_REGEX)
      ]
    }),
    confirmPassword: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    })
  }, { validators: passwordMatchValidator });

  // Real-time password requirement checklist signals
  protected readonly passwordValue = signal('');

  constructor() {
    this.form.controls.password.valueChanges.subscribe((val) => {
      this.passwordValue.set(val || '');
    });
  }

  protected readonly pwdHasMinLength = computed(() => this.passwordValue().length >= 12);
  protected readonly pwdHasUpper = computed(() => /[A-Z]/.test(this.passwordValue()));
  protected readonly pwdHasLower = computed(() => /[a-z]/.test(this.passwordValue()));
  protected readonly pwdHasNumber = computed(() => /\d/.test(this.passwordValue()));
  protected readonly pwdHasSpecial = computed(() => /[@$!%*?&_\-#.+='":;><,{}~`|\[\]\\\/]/.test(this.passwordValue()));

  protected toggleShowPassword(): void {
    this.showPassword.update((val) => !val);
  }

  protected toggleShowConfirmPassword(): void {
    this.showConfirmPassword.update((val) => !val);
  }

  protected onSubmit(): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const payload: RegisterPayload = {
      firstName: value.firstName.trim(),
      lastName: value.lastName.trim(),
      email: value.email.trim().toLowerCase(),
      phone: value.phone.trim(),
      password: value.password,
      confirmPassword: value.confirmPassword
    };

    this.loading.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.authApi.register(payload).pipe(
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: (res) => {
        this.successMessage.set(res.message || 'Votre compte a été créé avec succès.');
      },
      error: (err: unknown) => {
        if (err && typeof err === 'object' && 'status' in err) {
          const status = (err as { status: number }).status;
          const errorBody = (err as { error?: { message?: string; detail?: string; errors?: Array<{ field: string; message: string }> } }).error;
          if (status === 409) {
            this.errorMessage.set('Un compte existe déjà avec cette adresse email.');
            return;
          }
          if (status === 400) {
            const firstFieldError = errorBody?.errors?.[0]?.message;
            const msg = firstFieldError || errorBody?.detail || errorBody?.message;
            if (msg) {
              this.errorMessage.set(msg);
              return;
            }
          }
          if (status === 401) {
            this.errorMessage.set('Session non autorisée pour cette action.');
            return;
          }
        }
        this.errorMessage.set('Impossible de créer le compte pour le moment. Veuillez réessayer ultérieurement.');
      }
    });
  }

  protected showError(field: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || control.dirty);
  }

  protected showMismatchError(): boolean {
    return this.form.hasError('passwordMismatch') &&
      (this.form.controls.confirmPassword.touched || this.form.controls.confirmPassword.dirty);
  }
}

function passwordMatchValidator(group: AbstractControl): ValidationErrors | null {
  const pwd = group.get('password')?.value;
  const confirm = group.get('confirmPassword')?.value;
  return pwd && confirm && pwd !== confirm ? { passwordMismatch: true } : null;
}
