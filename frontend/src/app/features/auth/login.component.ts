import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { AuthService } from '../../core/services/auth.service';

interface DemoLogin {
  label: string;
  email: string;
}

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatIconModule, MatProgressBarModule
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  loading = signal(false);
  hidePassword = signal(true);

  form = this.fb.nonNullable.group({
    email: ['employee@hrgenius.com', [Validators.required, Validators.email]],
    password: ['Password@123', [Validators.required]]
  });

  // Convenience buttons so reviewers can jump into any role quickly.
  demoLogins: DemoLogin[] = [
    { label: 'Admin', email: 'admin@hrgenius.com' },
    { label: 'HR', email: 'hr@hrgenius.com' },
    { label: 'Payroll', email: 'payroll@hrgenius.com' },
    { label: 'Recruiter', email: 'recruiter@hrgenius.com' },
    { label: 'Manager', email: 'manager@hrgenius.com' },
    { label: 'Employee', email: 'employee@hrgenius.com' }
  ];

  useDemo(email: string): void {
    this.form.patchValue({ email, password: 'Password@123' });
  }

  submit(): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => {
        const redirect = this.route.snapshot.queryParamMap.get('redirect') || '/dashboard';
        this.router.navigateByUrl(redirect);
      },
      error: () => this.loading.set(false)
    });
  }
}
