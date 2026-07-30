export type UserRole = "employee" | "manager" | "finance";

export interface UserView {
  id: string;
  login_identifier: string;
  role: UserRole;
  manager_id: string | null;
}
