export const STAFF_ROLES = ['ADMIN', 'MANAGER', 'COMMERCIAL', 'TECHNICIAN'] as const;

export type StaffRole = typeof STAFF_ROLES[number];

export interface CreateStaffUserRequest {
  readonly firstName: string;
  readonly lastName: string;
  readonly email: string;
  readonly password: string;
  readonly role: StaffRole;
  readonly phone?: string;
}

export interface StaffUser {
  readonly id: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly email: string;
  readonly role: StaffRole;
  readonly enabled: boolean;
  readonly phone?: string;
}

export type StaffUserResponse = StaffUser;

export type ResetPasswordMode = 'DIRECT' | 'EMAIL';

export interface AdminResetPasswordPayload {
  readonly mode: ResetPasswordMode;
  readonly newPassword?: string;
  readonly confirmPassword?: string;
}

export interface AdminResetPasswordResponse {
  readonly success: boolean;
  readonly message: string;
}
