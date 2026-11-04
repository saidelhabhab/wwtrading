import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { finalize } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { DashboardLayoutComponent } from '../../shared/components/dashboard-layout/dashboard-layout.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { StaffUserApiService } from './staff-user-api.service';
import {
  AdminResetPasswordPayload,
  CreateStaffUserRequest,
  ResetPasswordMode,
  StaffRole,
  StaffUser
} from './staff-user.models';

interface RoleOption {
  readonly value: StaffRole;
  readonly label: string;
  readonly description: string;
}

const ADMIN_ROLE_OPTIONS: readonly RoleOption[] = [
  { value: 'ADMIN', label: 'Administrateur', description: 'Administration de la plateforme' },
  { value: 'MANAGER', label: 'Manager', description: 'Pilotage des activités' },
  { value: 'COMMERCIAL', label: 'Commercial', description: 'Gestion commerciale et devis' },
  { value: 'TECHNICIAN', label: 'Technicien', description: 'Interventions et maintenance' }
];

const MANAGER_ROLE_OPTIONS = ADMIN_ROLE_OPTIONS.filter(
  (option) => option.value === 'COMMERCIAL' || option.value === 'TECHNICIAN'
);

function passwordMatchValidator(group: AbstractControl): ValidationErrors | null {
  const pwd = group.get('newPassword')?.value;
  const confirm = group.get('confirmPassword')?.value;
  return pwd && confirm && pwd !== confirm ? { passwordMismatch: true } : null;
}

@Component({
  selector: 'wwt-staff-user-management',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatIconModule, DashboardLayoutComponent, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './staff-user-management.component.html',
  styleUrl: './staff-user-management.component.scss'
})
export class StaffUserManagementComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly staffUserApi = inject(StaffUserApiService);

  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);
  protected readonly success = signal<StaffUser | null>(null);
  protected readonly users = signal<readonly StaffUser[]>([]);
  protected readonly listLoading = signal(true);
  protected readonly listError = signal<string | null>(null);
  protected readonly passwordVisible = signal(false);
  protected readonly isAdmin = computed(() => this.auth.roles().includes('ADMIN'));
  protected readonly roleOptions = computed(() => this.isAdmin() ? ADMIN_ROLE_OPTIONS : MANAGER_ROLE_OPTIONS);
  protected readonly breadcrumb = computed(() => this.isAdmin()
    ? 'Administration / Utilisateurs'
    : 'Management / Utilisateurs');

  // Modal State for Password Reset
  protected readonly resetModalOpen = signal(false);
  protected readonly selectedUser = signal<StaffUser | null>(null);
  protected readonly resetMode = signal<ResetPasswordMode>('DIRECT');
  protected readonly resetSubmitting = signal(false);
  protected readonly resetError = signal<string | null>(null);
  protected readonly resetSuccess = signal<string | null>(null);
  protected readonly resetPasswordVisible = signal(false);
  protected readonly resetConfirmPasswordVisible = signal(false);
  protected readonly resetConfirmed = signal(false);

  // Form for Direct Password Reset
  protected readonly resetForm = new FormGroup({
    newPassword: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.minLength(12),
        Validators.maxLength(128),
        Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/)
      ]
    }),
    confirmPassword: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    })
  }, { validators: passwordMatchValidator });

  // Live password checklist signals
  protected readonly resetPwdValue = signal('');
  protected readonly pwdHasMinLength = computed(() => this.resetPwdValue().length >= 12);
  protected readonly pwdHasUpper = computed(() => /[A-Z]/.test(this.resetPwdValue()));
  protected readonly pwdHasLower = computed(() => /[a-z]/.test(this.resetPwdValue()));
  protected readonly pwdHasNumber = computed(() => /\d/.test(this.resetPwdValue()));
  protected readonly pwdHasSpecial = computed(() => /[^A-Za-z0-9]/.test(this.resetPwdValue()));

  // Form for Creating Staff Users
  protected readonly form = new FormGroup({
    firstName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]
    }),
    lastName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]
    }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(320)]
    }),
    phone: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(32), Validators.pattern(/^$|^\+?[0-9\s.\-()]{6,32}$/)]
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.minLength(12),
        Validators.maxLength(128),
        Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/)
      ]
    }),
    role: new FormControl<StaffRole | null>(null, Validators.required)
  });

  constructor() {
    this.resetForm.controls.newPassword.valueChanges.subscribe((val) => {
      this.resetPwdValue.set(val || '');
    });
  }

  ngOnInit(): void {
    this.loadUsers();
  }

  protected loadUsers(): void {
    if (this.listLoading() && this.users().length > 0) return;
    this.listLoading.set(true);
    this.listError.set(null);
    this.staffUserApi.getStaffUsers().pipe(
      finalize(() => this.listLoading.set(false))
    ).subscribe({
      next: (users) => this.users.set(users),
      error: (failure: unknown) => this.listError.set(this.listErrorMessage(failure))
    });
  }

  protected submit(): void {
    if (this.submitting()) return;
    this.normalizeIdentityFields();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    if (!value.role || !this.roleOptions().some((option) => option.value === value.role)) {
      this.form.controls.role.setErrors({ unauthorizedRole: true });
      this.submitError.set('Le rôle sélectionné n’est pas autorisé pour votre compte.');
      return;
    }

    const phoneTrimmed = value.phone.trim();
    const request: CreateStaffUserRequest = {
      firstName: value.firstName.trim(),
      lastName: value.lastName.trim(),
      email: value.email.trim().toLowerCase(),
      password: value.password,
      role: value.role,
      phone: phoneTrimmed ? phoneTrimmed : undefined
    };

    if (!request.firstName || !request.lastName) {
      this.form.controls.firstName.updateValueAndValidity();
      this.form.controls.lastName.updateValueAndValidity();
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.submitError.set(null);
    this.success.set(null);
    this.form.disable();

    this.staffUserApi.createStaffUser(request).pipe(
      finalize(() => {
        this.submitting.set(false);
        this.form.enable();
      })
    ).subscribe({
      next: (created) => {
        this.success.set(created);
        this.passwordVisible.set(false);
        this.form.reset({ firstName: '', lastName: '', email: '', phone: '', password: '', role: null });
        this.loadUsers();
      },
      error: (failure: unknown) => {
        this.form.controls.password.setValue('');
        this.passwordVisible.set(false);
        this.submitError.set(this.errorMessage(failure));
      }
    });
  }

  // Password Reset Modal Handlers
  protected openResetModal(user: StaffUser): void {
    this.selectedUser.set(user);
    this.resetMode.set('DIRECT');
    this.resetSubmitting.set(false);
    this.resetError.set(null);
    this.resetSuccess.set(null);
    this.resetPasswordVisible.set(false);
    this.resetConfirmPasswordVisible.set(false);
    this.resetConfirmed.set(false);
    this.resetForm.reset({ newPassword: '', confirmPassword: '' });
    this.resetPwdValue.set('');
    this.resetModalOpen.set(true);
  }

  protected closeResetModal(): void {
    if (this.resetSubmitting()) return;
    this.resetModalOpen.set(false);
    this.selectedUser.set(null);
    this.resetForm.reset({ newPassword: '', confirmPassword: '' });
    this.resetPwdValue.set('');
    this.resetError.set(null);
    this.resetSuccess.set(null);
    this.resetConfirmed.set(false);
  }

  protected setResetMode(mode: ResetPasswordMode): void {
    this.resetMode.set(mode);
    this.resetError.set(null);
    this.resetSuccess.set(null);
  }

  protected toggleResetPasswordVisibility(): void {
    this.resetPasswordVisible.update((v) => !v);
  }

  protected toggleResetConfirmPasswordVisibility(): void {
    this.resetConfirmPasswordVisible.update((v) => !v);
  }

  protected toggleResetConfirmed(): void {
    this.resetConfirmed.update((c) => !c);
  }

  protected isResetFormInvalid(): boolean {
    if (this.resetMode() === 'DIRECT') {
      return this.resetForm.invalid || !this.resetConfirmed();
    }
    return !this.resetConfirmed();
  }

  protected showResetError(controlName: keyof typeof this.resetForm.controls): boolean {
    const control = this.resetForm.controls[controlName];
    return control.invalid && (control.touched || control.dirty);
  }

  protected showResetMismatchError(): boolean {
    return this.resetForm.hasError('passwordMismatch') &&
      (this.resetForm.controls.confirmPassword.touched || this.resetForm.controls.confirmPassword.dirty);
  }

  protected submitResetPassword(): void {
    const user = this.selectedUser();
    if (!user || this.resetSubmitting()) return;

    if (this.resetMode() === 'DIRECT') {
      if (this.resetForm.invalid) {
        this.resetForm.markAllAsTouched();
        return;
      }
    }

    if (!this.resetConfirmed()) {
      this.resetError.set('Veuillez cocher la case de confirmation avant de valider.');
      return;
    }

    this.resetSubmitting.set(true);
    this.resetError.set(null);
    this.resetSuccess.set(null);

    const payload: AdminResetPasswordPayload = {
      mode: this.resetMode(),
      newPassword: this.resetMode() === 'DIRECT' ? this.resetForm.controls.newPassword.value : undefined,
      confirmPassword: this.resetMode() === 'DIRECT' ? this.resetForm.controls.confirmPassword.value : undefined
    };

    this.staffUserApi.resetUserPassword(user.id, payload).pipe(
      finalize(() => this.resetSubmitting.set(false))
    ).subscribe({
      next: (res) => {
        this.resetSuccess.set(res.message || 'Le mot de passe a été réinitialisé avec succès.');
        this.resetForm.reset({ newPassword: '', confirmPassword: '' });
        this.resetPwdValue.set('');
        setTimeout(() => {
          if (this.resetModalOpen()) {
            this.closeResetModal();
          }
        }, 2200);
      },
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse) {
          if (err.status === 403) {
            this.resetError.set('Action non autorisée. Rôle Administrateur requis.');
            return;
          }
          if (err.status === 404) {
            this.resetError.set('Utilisateur introuvable dans l’annuaire.');
            return;
          }
          if (err.error?.message) {
            this.resetError.set(err.error.message);
            return;
          }
        }
        this.resetError.set('Une erreur est survenue lors de la réinitialisation du mot de passe.');
      }
    });
  }

  protected showError(controlName: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[controlName];
    return control.invalid && (control.touched || control.dirty);
  }

  protected togglePasswordVisibility(): void {
    this.passwordVisible.update((visible) => !visible);
  }

  protected roleLabel(role?: StaffRole | null): string {
    if (!role) return '';
    return ADMIN_ROLE_OPTIONS.find((option) => option.value === role)?.label ?? role;
  }

  protected roleIcon(role: StaffRole): string {
    switch (role) {
      case 'ADMIN': return 'admin_panel_settings';
      case 'MANAGER': return 'supervisor_account';
      case 'COMMERCIAL': return 'business_center';
      case 'TECHNICIAN': return 'engineering';
    }
  }

  private normalizeIdentityFields(): void {
    this.form.controls.firstName.setValue(this.form.controls.firstName.value.trim());
    this.form.controls.lastName.setValue(this.form.controls.lastName.value.trim());
    this.form.controls.email.setValue(this.form.controls.email.value.trim().toLowerCase());
    this.form.controls.phone.setValue(this.form.controls.phone.value.trim());
  }

  private errorMessage(failure: unknown): string {
    if (!(failure instanceof HttpErrorResponse)) return 'Le service Utilisateurs est momentanément indisponible.';
    if (failure.status === 0) return 'Le service Utilisateurs est inaccessible. Réessayez plus tard.';
    if (failure.status === 400) return 'Certaines données sont invalides. Vérifiez le formulaire.';
    if (failure.status === 401) return 'Votre session a expiré. Reconnectez-vous avant de réessayer.';
    if (failure.status === 403) return 'Vous n’êtes pas autorisé à créer cet utilisateur.';
    if (failure.status === 409) return 'Un utilisateur utilise déjà cette adresse email.';
    if (failure.status === 502) return 'Le fournisseur d’identité est momentanément indisponible.';
    return 'La création a échoué. Réessayez plus tard.';
  }

  private listErrorMessage(failure: unknown): string {
    if (!(failure instanceof HttpErrorResponse)) return 'La liste des utilisateurs ne peut pas être chargée.';
    if (failure.status === 0) return 'Le service Utilisateurs est inaccessible. Réessayez plus tard.';
    if (failure.status === 401) return 'Votre session a expiré. Reconnectez-vous pour continuer.';
    if (failure.status === 403) return 'Vous n’êtes pas autorisé à consulter les utilisateurs.';
    if (failure.status === 502 || failure.status >= 500) return 'L’annuaire est momentanément indisponible.';
    return 'La liste des utilisateurs ne peut pas être chargée.';
  }
}
