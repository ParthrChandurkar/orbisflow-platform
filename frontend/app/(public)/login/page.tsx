import { Suspense } from "react";
import { LoginForm } from "@/components/auth/login-form";

export default function LoginPage() {
  return (
    <main className="login-page">
      <section className="login-copy">
        <div className="brand light">
          <span className="brand-mark">O</span>
          <span>Orbis Flow</span>
        </div>
        <div>
          <span className="eyebrow light-text">AI-assisted invoice workflow</span>
          <h1>Move every invoice forward with clarity.</h1>
          <p>
            Submit documents, resolve extraction issues, and follow each
            approval from one focused workspace.
          </p>
        </div>
        <small>Secure access for Employee, Manager, and Finance roles.</small>
      </section>
      <section className="login-panel">
        <div className="login-card">
          <span className="eyebrow">Welcome back</span>
          <h2>Sign in to your workspace</h2>
          <p className="muted">Use your Orbis Flow credentials to continue.</p>
          <Suspense fallback={<p>Loading sign in…</p>}>
            <LoginForm />
          </Suspense>
        </div>
      </section>
    </main>
  );
}
