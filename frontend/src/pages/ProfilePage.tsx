import { useEffect, useState, type FormEvent } from "react"

import { AFFrame } from "@/components/ui/af-frame"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

const NETWORK_ERROR = "Não foi possível conectar. Confira sua internet e tente de novo."
const GENERIC_ERROR = "Não foi possível salvar. Tente de novo."
const LOAD_ERROR = "Não foi possível carregar o perfil. Tente de novo."
const SESSION_EXPIRED = "Sua sessão expirou. Volte e entre de novo."

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

type ProfilePageProps = {
  onBack: () => void
  onOpenServices: () => void
}

export default function ProfilePage({ onBack, onOpenServices }: ProfilePageProps) {
  const [loading, setLoading] = useState(true)
  const [sessionExpired, setSessionExpired] = useState(false)
  const [email, setEmail] = useState("")
  const [name, setName] = useState("")
  const [whatsapp, setWhatsapp] = useState("")
  const [timezone, setTimezone] = useState("")
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)
  const [publicSlug, setPublicSlug] = useState("")
  const [newSlug, setNewSlug] = useState("")
  const [slugSubmitting, setSlugSubmitting] = useState(false)
  const [slugError, setSlugError] = useState<string | null>(null)
  const [slugSuccess, setSlugSuccess] = useState(false)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const response = await fetch(`${API}/api/photographers/me`, { credentials: "include" })

        if (cancelled) return

        if (response.status === 401) {
          setSessionExpired(true)
        } else if (response.ok) {
          const body = await response.json()
          setEmail(body.email ?? "")
          setName(body.name ?? "")
          setWhatsapp(body.whatsapp ?? "")
          setTimezone(body.timezone ?? "")
          setPublicSlug(body.publicSlug ?? "")
          setNewSlug(body.publicSlug ?? "")
        } else {
          setFormError((await readMessage(response)) ?? LOAD_ERROR)
        }
      } catch {
        if (!cancelled) setFormError(NETWORK_ERROR)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setFormError(null)
    setSuccess(false)

    try {
      const response = await fetch(`${API}/api/photographers/me`, {
        method: "PUT",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
          "X-XSRF-TOKEN": readCookie("XSRF-TOKEN"),
        },
        body: JSON.stringify({ name, whatsapp, timezone }),
      })

      if (response.status === 204) {
        setSuccess(true)
      } else if (response.status === 401) {
        setSessionExpired(true)
      } else {
        setFormError((await readMessage(response)) ?? GENERIC_ERROR)
      }
    } catch {
      setFormError(NETWORK_ERROR)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleSlugSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSlugSubmitting(true)
    setSlugError(null)
    setSlugSuccess(false)

    try {
      const response = await fetch(`${API}/api/photographers/me/slug`, {
        method: "PUT",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
          "X-XSRF-TOKEN": readCookie("XSRF-TOKEN"),
        },
        body: JSON.stringify({ slug: newSlug }),
      })

      if (response.status === 204) {
        const saved = newSlug.trim().toLowerCase()
        setPublicSlug(saved)
        setNewSlug(saved)
        setSlugSuccess(true)
      } else if (response.status === 401) {
        setSessionExpired(true)
      } else {
        setSlugError((await readMessage(response)) ?? GENERIC_ERROR)
      }
    } catch {
      setSlugError(NETWORK_ERROR)
    } finally {
      setSlugSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen bg-paper px-8 py-10 min-[960px]:px-16 min-[960px]:py-14">
      <div className="w-full max-w-[440px] text-left">
        <p className="mb-2 font-sans text-sm text-ink-soft">JM Code Studio</p>
        <h1 className="mb-8 font-display text-3xl text-ink">Editar perfil</h1>

        {sessionExpired ? (
          <div className="flex flex-col items-start gap-6">
            <p role="alert" className="text-sm text-danger">
              {SESSION_EXPIRED}
            </p>
            <Button type="button" variant="secondary" onClick={onBack}>
              Voltar
            </Button>
          </div>
        ) : loading ? (
          <p role="status" className="text-sm text-ink-soft">
            Carregando…
          </p>
        ) : (
          <>
            <form onSubmit={handleSubmit} className="flex flex-col gap-6">
              <div className="flex flex-col gap-2">
                <Label htmlFor="profile-email">E-mail</Label>
                <AFFrame>
                  <Input
                    id="profile-email"
                    type="email"
                    className="af-frame-input"
                    value={email}
                    disabled
                    readOnly
                  />
                </AFFrame>
              </div>

              <div className="flex flex-col gap-2">
                <Label htmlFor="profile-name">Nome</Label>
                <AFFrame>
                  <Input
                    id="profile-name"
                    type="text"
                    className="af-frame-input"
                    required
                    maxLength={150}
                    autoComplete="name"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    aria-describedby={formError ? "form-error" : undefined}
                  />
                </AFFrame>
              </div>

              <div className="flex flex-col gap-2">
                <Label htmlFor="profile-whatsapp">WhatsApp (opcional)</Label>
                <AFFrame>
                  <Input
                    id="profile-whatsapp"
                    type="text"
                    className="af-frame-input"
                    autoComplete="tel"
                    value={whatsapp}
                    onChange={(e) => setWhatsapp(e.target.value)}
                    aria-describedby={formError ? "form-error" : undefined}
                  />
                </AFFrame>
              </div>

              <div className="flex flex-col gap-2">
                <Label htmlFor="profile-timezone">Fuso horário</Label>
                <AFFrame>
                  <Input
                    id="profile-timezone"
                    type="text"
                    className="af-frame-input"
                    required
                    placeholder="America/Sao_Paulo"
                    value={timezone}
                    onChange={(e) => setTimezone(e.target.value)}
                    aria-describedby={formError ? "form-error" : undefined}
                  />
                </AFFrame>
              </div>

              <div className="flex flex-col gap-3">
                <Button type="submit" disabled={submitting}>
                  {submitting ? "Salvando…" : "Salvar"}
                </Button>
                {success && (
                  <p role="status" className="text-sm text-ink">
                    Perfil atualizado.
                  </p>
                )}
                {formError && (
                  <p id="form-error" role="alert" className="text-sm text-danger">
                    {formError}
                  </p>
                )}
              </div>
            </form>

            <form onSubmit={handleSlugSubmit} className="mt-10 flex flex-col gap-6">
              <div className="flex flex-col gap-2">
                <h2 className="font-display text-2xl text-ink">Endereço público</h2>
                <p className="text-sm text-ink-soft">
                  Seu link: produto.com.br/{publicSlug}
                </p>
              </div>

              <div className="flex flex-col gap-2">
                <Label htmlFor="profile-slug">Novo endereço</Label>
                <AFFrame>
                  <Input
                    id="profile-slug"
                    type="text"
                    className="af-frame-input"
                    required
                    maxLength={100}
                    autoComplete="off"
                    value={newSlug}
                    onChange={(e) => setNewSlug(e.target.value)}
                    aria-describedby={slugError ? "slug-error" : "slug-hint"}
                  />
                </AFFrame>
                <p id="slug-hint" className="text-xs text-ink-soft">
                  Trocar o endereço invalida o link anterior.
                </p>
              </div>

              <div className="flex flex-col gap-3">
                <Button type="submit" disabled={slugSubmitting}>
                  {slugSubmitting ? "Salvando…" : "Salvar endereço"}
                </Button>
                {slugSuccess && (
                  <p role="status" className="text-sm text-ink">
                    Endereço atualizado.
                  </p>
                )}
                {slugError && (
                  <p id="slug-error" role="alert" className="text-sm text-danger">
                    {slugError}
                  </p>
                )}
              </div>
            </form>

            <p className="mt-6 flex gap-6 text-sm text-ink-soft">
              <button
                type="button"
                onClick={onOpenServices}
                className="text-ink underline underline-offset-2"
              >
                Meus serviços
              </button>
              <button
                type="button"
                onClick={onBack}
                className="text-ink underline underline-offset-2"
              >
                Voltar
              </button>
            </p>
          </>
        )}
      </div>
    </div>
  )
}
