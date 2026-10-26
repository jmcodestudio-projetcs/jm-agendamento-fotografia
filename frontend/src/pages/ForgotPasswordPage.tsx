import { useState, type FormEvent } from "react"

import { AFFrame } from "@/components/ui/af-frame"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

const NETWORK_ERROR = "Não foi possível conectar. Confira sua internet e tente de novo."

const API = import.meta.env.VITE_API_BASE_URL

type ForgotPasswordPageProps = {
  onBackToLogin: () => void
}

export default function ForgotPasswordPage({ onBackToLogin }: ForgotPasswordPageProps) {
  const [email, setEmail] = useState("")
  const [submitting, setSubmitting] = useState(false)
  const [sent, setSent] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setFormError(null)

    try {
      const response = await fetch(`${API}/api/public/photographers/password-reset/request`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email }),
      })

      if (response.status === 200) {
        setSent(true)
      } else {
        // Só chega aqui em falha inesperada (ex.: validação do formato do e-mail).
        setFormError("Não foi possível enviar o link. Confira o e-mail e tente de novo.")
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
          <h2 className="mb-4 font-display text-3xl text-ink">Esqueci minha senha</h2>

          {sent ? (
            <p role="status" className="text-base text-ink">
              Se esse e-mail existir na nossa base, enviamos um link de redefinição. Confira sua
              caixa de entrada.
            </p>
          ) : (
            <>
              <p className="mb-8 text-base text-ink-soft">
                Informe seu e-mail e enviaremos um link para redefinir sua senha.
              </p>
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

                <div className="flex flex-col gap-3">
                  <Button type="submit" disabled={submitting}>
                    {submitting ? "Enviando…" : "Enviar link"}
                  </Button>
                  {formError && (
                    <p id="form-error" role="alert" className="text-sm text-danger">
                      {formError}
                    </p>
                  )}
                </div>
              </form>
            </>
          )}

          <p className="mt-6 text-sm text-ink-soft">
            Lembrou a senha?{" "}
            <button
              type="button"
              onClick={onBackToLogin}
              className="text-ink underline underline-offset-2"
            >
              Entrar
            </button>
          </p>
        </div>
      </section>
    </div>
  )
}
