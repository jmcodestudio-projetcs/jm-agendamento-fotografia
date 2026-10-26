import { useState, type FormEvent } from "react"

import { AFFrame } from "@/components/ui/af-frame"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

const NETWORK_ERROR = "Não foi possível conectar. Confira sua internet e tente de novo."
const GENERIC_ERROR = "Não foi possível salvar a nova senha. Tente de novo."

const API = import.meta.env.VITE_API_BASE_URL

async function readMessage(response: Response): Promise<string | null> {
  try {
    const body = await response.json()
    return typeof body?.message === "string" ? body.message : null
  } catch {
    return null
  }
}

type ResetPasswordPageProps = {
  token: string
  onDone: () => void
}

export default function ResetPasswordPage({ token, onDone }: ResetPasswordPageProps) {
  const [newPassword, setNewPassword] = useState("")
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState(false)
  const [tokenInvalid, setTokenInvalid] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setFormError(null)
    setTokenInvalid(false)

    try {
      const response = await fetch(`${API}/api/public/photographers/password-reset/confirm`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token, newPassword }),
      })

      if (response.status === 200) {
        setDone(true)
        setNewPassword("")
      } else {
        if (response.status === 400) {
          const body = await response
            .clone()
            .json()
            .catch(() => null)
          if (body?.error === "TOKEN_INVALIDO") {
            setTokenInvalid(true)
          }
        }
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
          <h2 className="mb-8 font-display text-3xl text-ink">Nova senha</h2>

          {done ? (
            <div className="flex flex-col items-start gap-6">
              <p role="status" className="text-base text-ink">
                Senha redefinida. Faça login com a nova senha.
              </p>
              <Button type="button" onClick={onDone}>
                Ir para o login
              </Button>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="flex flex-col gap-6">
              <div className="flex flex-col gap-2">
                <Label htmlFor="newPassword">Nova senha</Label>
                <AFFrame>
                  <Input
                    id="newPassword"
                    type="password"
                    className="af-frame-input"
                    required
                    autoComplete="new-password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    aria-describedby={formError ? "form-error" : undefined}
                  />
                </AFFrame>
              </div>

              <div className="flex flex-col gap-3">
                <Button type="submit" disabled={submitting}>
                  {submitting ? "Salvando…" : "Salvar nova senha"}
                </Button>
                {formError && (
                  <p id="form-error" role="alert" className="text-sm text-danger">
                    {formError}
                  </p>
                )}
                {tokenInvalid && (
                  <button
                    type="button"
                    onClick={onDone}
                    className="self-start text-sm text-ink underline underline-offset-2"
                  >
                    Solicitar novo link
                  </button>
                )}
              </div>
            </form>
          )}
        </div>
      </section>
    </div>
  )
}
