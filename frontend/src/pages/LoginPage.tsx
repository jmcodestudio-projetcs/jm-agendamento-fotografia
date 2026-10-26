import { useEffect, useState, type FormEvent } from "react"

import { AFFrame } from "@/components/ui/af-frame"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

const NETWORK_ERROR = "Não foi possível conectar. Confira sua internet e tente de novo."
const GENERIC_ERROR = "Não foi possível entrar. Tente de novo."

const API = import.meta.env.VITE_API_BASE_URL

function readCookie(name: string): string {
  const prefix = `${name}=`
  const entry = document.cookie.split("; ").find((c) => c.startsWith(prefix))
  return entry ? decodeURIComponent(entry.slice(prefix.length)) : ""
}

async function readMessage(response: Response): Promise<string | null> {
  try {
    const body = await response.json()
    return typeof body?.message === "string" ? body.message : null
  } catch {
    return null
  }
}

type LoginPageProps = {
  onSwitchToSignup: () => void
  onForgotPassword: () => void
}

export default function LoginPage({ onSwitchToSignup, onForgotPassword }: LoginPageProps) {
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [submitting, setSubmitting] = useState(false)
  const [loggedInName, setLoggedInName] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

  useEffect(() => {
    // Grava o cookie XSRF-TOKEN antes de o usuário conseguir enviar o formulário.
    fetch(`${API}/api/public/csrf-token`, { credentials: "include" }).catch(() => {})
  }, [])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setFormError(null)

    try {
      const response = await fetch(`${API}/api/public/photographers/login`, {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
          "X-XSRF-TOKEN": readCookie("XSRF-TOKEN"),
        },
        body: JSON.stringify({ email, password }),
      })

      if (response.status === 200) {
        const body = await response.json()
        setLoggedInName(typeof body?.name === "string" ? body.name : "")
        setPassword("")
      } else {
        setFormError((await readMessage(response)) ?? GENERIC_ERROR)
      }
    } catch {
      setFormError(NETWORK_ERROR)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleLogout() {
    setSubmitting(true)
    setFormError(null)

    try {
      const response = await fetch(`${API}/api/photographers/logout`, {
        method: "POST",
        credentials: "include",
        headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") },
      })

      if (response.ok) {
        setLoggedInName(null)
      } else {
        setFormError((await readMessage(response)) ?? "Não foi possível sair. Tente de novo.")
      }
    } catch {
      setFormError(NETWORK_ERROR)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="grid min-h-screen grid-cols-1 min-[960px]:grid-cols-2">
      <section className="flex flex-col items-start justify-start gap-8 bg-ink px-8 py-10 text-left text-paper min-[960px]:px-16 min-[960px]:py-14">
        <p className="font-sans text-sm text-paper/70">JM Code Studio</p>
        <div className="flex flex-col items-start gap-5 min-[960px]:mt-24">
          <h1 className="max-w-[14ch] font-display text-5xl leading-[1.1] min-[960px]:text-6xl">
            Sua agenda, organizada como um bom ensaio.
          </h1>
          <p className="max-w-[40ch] font-sans text-base text-paper/70">
            Agendamento de ensaios fotográficos, do pedido à confirmação.
          </p>
        </div>
      </section>

      <section className="bg-paper px-8 py-10 min-[960px]:px-16 min-[960px]:py-14">
        <div className="w-full max-w-[440px] text-left">
          {loggedInName !== null ? (
            <div className="flex flex-col items-start gap-6">
              <p role="status" className="font-display text-2xl text-ink">
                Login feito, {loggedInName}.
              </p>
              <Button type="button" variant="secondary" onClick={handleLogout} disabled={submitting}>
                {submitting ? "Saindo…" : "Sair"}
              </Button>
              {formError && (
                <p role="alert" className="text-sm text-danger">
                  {formError}
                </p>
              )}
            </div>
          ) : (
            <>
              <h2 className="mb-8 font-display text-3xl text-ink">Entrar</h2>
              <form onSubmit={handleSubmit} className="flex flex-col gap-6">
                <div className="flex flex-col gap-2">
                  <Label htmlFor="email">E-mail</Label>
                  <AFFrame>
                    <Input
                      id="email"
                      type="email"
                      className="af-frame-input"
                      required
                      autoComplete="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      aria-describedby={formError ? "form-error" : undefined}
                    />
                  </AFFrame>
                </div>

                <div className="flex flex-col gap-2">
                  <Label htmlFor="password">Senha</Label>
                  <AFFrame>
                    <Input
                      id="password"
                      type="password"
                      className="af-frame-input"
                      required
                      autoComplete="current-password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      aria-describedby={formError ? "form-error" : undefined}
                    />
                  </AFFrame>
                </div>

                <div className="flex flex-col gap-3">
                  <Button type="submit" disabled={submitting}>
                    {submitting ? "Entrando…" : "Entrar"}
                  </Button>
                  {formError && (
                    <p id="form-error" role="alert" className="text-sm text-danger">
                      {formError}
                    </p>
                  )}
                </div>
              </form>

              <p className="mt-6 text-sm text-ink-soft">
                <button
                  type="button"
                  onClick={onForgotPassword}
                  className="text-ink underline underline-offset-2"
                >
                  Esqueci minha senha?
                </button>
              </p>

              <p className="mt-3 text-sm text-ink-soft">
                Ainda não tem conta?{" "}
                <button
                  type="button"
                  onClick={onSwitchToSignup}
                  className="text-ink underline underline-offset-2"
                >
                  Criar conta
                </button>
              </p>
            </>
          )}
        </div>
      </section>
    </div>
  )
}
