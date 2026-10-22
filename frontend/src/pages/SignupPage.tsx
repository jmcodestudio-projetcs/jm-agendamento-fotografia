import { useState, type FormEvent } from "react"

import { AFFrame } from "@/components/ui/af-frame"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { cn } from "@/lib/utils"

const TIMEZONES = [
  { value: "America/Sao_Paulo", label: "Horário de Brasília" },
  { value: "America/Manaus", label: "Amazonas" },
  { value: "America/Rio_Branco", label: "Acre" },
  { value: "America/Noronha", label: "Fernando de Noronha" },
]

const NETWORK_ERROR = "Não foi possível conectar. Confira sua internet e tente de novo."
const GENERIC_ERROR = "Não foi possível criar a conta. Tente de novo."

async function readMessage(response: Response): Promise<string | null> {
  try {
    const body = await response.json()
    return typeof body?.message === "string" ? body.message : null
  } catch {
    return null
  }
}

type SignupPageProps = {
  onSwitchToLogin: () => void
}

export default function SignupPage({ onSwitchToLogin }: SignupPageProps) {
  const [name, setName] = useState("")
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [timezone, setTimezone] = useState("America/Sao_Paulo")
  const [submitting, setSubmitting] = useState(false)
  const [success, setSuccess] = useState(false)
  const [emailError, setEmailError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setEmailError(null)
    setFormError(null)

    try {
      const response = await fetch(
        `${import.meta.env.VITE_API_BASE_URL}/api/public/photographers/signup`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ name, email, password, timezone }),
        },
      )

      if (response.status === 201) {
        setSuccess(true)
      } else if (response.status === 409) {
        setEmailError((await readMessage(response)) ?? "Este e-mail já está cadastrado.")
      } else {
        setFormError((await readMessage(response)) ?? GENERIC_ERROR)
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
          {success ? (
            <div className="flex flex-col items-start gap-6">
              <p role="status" className="font-display text-2xl text-ink">
                Conta criada. Você já pode entrar.
              </p>
              <Button type="button" onClick={onSwitchToLogin}>
                Entrar
              </Button>
            </div>
          ) : (
            <>
              <h2 className="mb-8 font-display text-3xl text-ink">Criar conta</h2>
              <form onSubmit={handleSubmit} className="flex flex-col gap-6">
                <div className="flex flex-col gap-2">
                  <Label htmlFor="name">Nome</Label>
                  <AFFrame>
                    <Input
                      id="name"
                      type="text"
                      className="af-frame-input"
                      required
                      placeholder="Seu nome ou o nome do seu estúdio"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                    />
                  </AFFrame>
                </div>

                <div className="flex flex-col gap-2">
                  <Label htmlFor="email">E-mail</Label>
                  <AFFrame>
                    <Input
                      id="email"
                      type="email"
                      className="af-frame-input"
                      required
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      aria-invalid={emailError ? true : undefined}
                      aria-describedby={emailError ? "email-error" : undefined}
                    />
                  </AFFrame>
                  {emailError && (
                    <p id="email-error" className="text-sm text-danger">
                      {emailError}
                    </p>
                  )}
                </div>

                <div className="flex flex-col gap-2">
                  <Label htmlFor="password">Senha</Label>
                  <AFFrame>
                    <Input
                      id="password"
                      type="password"
                      className="af-frame-input"
                      required
                      autoComplete="new-password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      aria-describedby="password-hint"
                    />
                  </AFFrame>
                  <p id="password-hint" className="text-sm text-ink-soft">
                    Mínimo de 15 caracteres.
                  </p>
                </div>

                <div className="flex flex-col gap-2">
                  <Label htmlFor="timezone">Fuso horário</Label>
                  <AFFrame>
                    <select
                      id="timezone"
                      className={cn(
                        "flex h-11 w-full rounded-[6px] border border-line bg-surface px-3 text-[15px] text-ink outline-none transition-colors disabled:cursor-not-allowed disabled:opacity-50",
                        "af-frame-input",
                      )}
                      value={timezone}
                      onChange={(e) => setTimezone(e.target.value)}
                    >
                      {TIMEZONES.map((tz) => (
                        <option key={tz.value} value={tz.value}>
                          {tz.label}
                        </option>
                      ))}
                    </select>
                  </AFFrame>
                </div>

                <div className="flex flex-col gap-3">
                  <Button type="submit" disabled={submitting}>
                    {submitting ? "Criando conta…" : "Criar conta"}
                  </Button>
                  {formError && (
                    <p role="alert" className="text-sm text-danger">
                      {formError}
                    </p>
                  )}
                </div>
              </form>

              <p className="mt-6 text-sm text-ink-soft">
                Já tem conta?{" "}
                <button
                  type="button"
                  onClick={onSwitchToLogin}
                  className="text-ink underline underline-offset-2"
                >
                  Entrar
                </button>
              </p>
            </>
          )}
        </div>
      </section>
    </div>
  )
}
