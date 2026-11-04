import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService, resolveSafeReturnUrl } from '../../core/auth/auth.service';
import { resolvePostLoginRoute } from './login-redirect.component';

@Component({
  selector: 'wwt-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly showPassword = signal(false);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly lang = signal<'FR' | 'EN'>('FR');

  protected toggleLanguage(): void {
    this.lang.update((l) => (l === 'FR' ? 'EN' : 'FR'));
  }

  protected readonly returnUrl = computed(() => {
    return resolveSafeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));
  });

  protected readonly form = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email]
    })
  });

  ngOnInit(): void {
    if (this.auth.authenticated()) {
      const roles = this.auth.roles();
      const destination = resolvePostLoginRoute(this.returnUrl(), roles, this.router.config);
      void this.router.navigateByUrl(destination, { replaceUrl: true });
    }
  }

  protected toggleShowPassword(): void {
    this.showPassword.update((val) => !val);
  }

  protected onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    // Redirect to Keycloak authentication flow
    void this.auth.login(this.returnUrl());
  }

  protected triggerDirectKeycloakLogin(): void {
    this.loading.set(true);
    void this.auth.login(this.returnUrl());
  }

  protected showError(field: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || control.dirty);
  }
}
