import { useCallback, useEffect, useState, type FormEvent } from "react"

import { AFFrame } from "@/components/ui/af-frame"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

const NETWORK_ERROR = "Não foi possível conectar. Confira sua internet e tente de novo."
const GENERIC_ERROR = "Não foi possível salvar. Tente de novo."
const LOAD_ERROR = "Não foi possível carregar os serviços. Tente de novo."
const SESSION_EXPIRED = "Sua sessão expirou. Volte e entre de novo."

const API = import.meta.env.VITE_API_BASE_URL

const brl = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" })

type Service = {
  id: string
  name: string
  description: string | null
  durationMinutes: number
  priceAmount: number | null
  pricePublic: boolean
  minimumNoticeMinutes: number
  bufferAfterMinutes: number
  active: boolean
}

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

type ServicesPageProps = {
  onBack: () => void
}

export default function ServicesPage({ onBack }: ServicesPageProps) {
  const [loading, setLoading] = useState(true)
  const [sessionExpired, setSessionExpired] = useState(false)
  const [services, setServices] = useState<Service[]>([])
  const [listError, setListError] = useState<string | null>(null)

  const [formVisible, setFormVisible] = useState(false)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [name, setName] = useState("")
  const [description, setDescription] = useState("")
  const [durationMinutes, setDurationMinutes] = useState("")
  const [priceAmount, setPriceAmount] = useState("")
  const [pricePublic, setPricePublic] = useState(false)
  const [minimumNoticeMinutes, setMinimumNoticeMinutes] = useState("0")
  const [bufferAfterMinutes, setBufferAfterMinutes] = useState("0")
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const loadServices = useCallback(async (): Promise<boolean> => {
    try {
      const response = await fetch(`${API}/api/services`, { credentials: "include" })

      if (response.status === 401) {
        setSessionExpired(true)
        return false
      }
      if (response.ok) {
        setServices(await response.json())
        setListError(null)
        return true
      }
      setListError((await readMessage(response)) ?? LOAD_ERROR)
    } catch {
      setListError(NETWORK_ERROR)
    }
    return false
  }, [])

  useEffect(() => {
    let cancelled = false

    async function load() {
      await loadServices()
      if (!cancelled) setLoading(false)
    }

    load()
    return () => {
      cancelled = true
    }
  }, [loadServices])

  function resetForm() {
    setSelectedId(null)
    setName("")
    setDescription("")
    setDurationMinutes("")
    setPriceAmount("")
    setPricePublic(false)
    setMinimumNoticeMinutes("0")
    setBufferAfterMinutes("0")
    setFormError(null)
  }

  function handleNew() {
    resetForm()
    setFormVisible(true)
  }

  function handleEdit(service: Service) {
    setSelectedId(service.id)
    setName(service.name)
    setDescription(service.description ?? "")
    setDurationMinutes(String(service.durationMinutes))
    setPriceAmount(service.priceAmount === null ? "" : String(service.priceAmount))
    setPricePublic(service.pricePublic && service.priceAmount !== null)
    setMinimumNoticeMinutes(String(service.minimumNoticeMinutes))
    setBufferAfterMinutes(String(service.bufferAfterMinutes))
    setFormError(null)
    setFormVisible(true)
  }

  function handlePriceChange(value: string) {
    setPriceAmount(value)
    if (value.trim() === "") setPricePublic(false)
  }

  async function handleToggleActive(service: Service) {
    setListError(null)

    try {
      const response = await fetch(`${API}/api/services/${service.id}/active`, {
        method: "PUT",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
          "X-XSRF-TOKEN": readCookie("XSRF-TOKEN"),
        },
        body: JSON.stringify({ active: !service.active }),
      })

      if (response.ok) {
        const updated: Service = await response.json()
        setServices((current) => current.map((s) => (s.id === updated.id ? updated : s)))
      } else if (response.status === 401) {
        setSessionExpired(true)
      } else {
        setListError((await readMessage(response)) ?? GENERIC_ERROR)
      }
    } catch {
      setListError(NETWORK_ERROR)
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setFormError(null)

    const hasPrice = priceAmount.trim() !== ""
    const payload = {
      name,
      description: description.trim() === "" ? null : description,
      durationMinutes: Number(durationMinutes),
      priceAmount: hasPrice ? Number(priceAmount) : null,
      pricePublic: hasPrice && pricePublic,
      minimumNoticeMinutes: Number(minimumNoticeMinutes),
      bufferAfterMinutes: Number(bufferAfterMinutes),
    }

    try {
      const response = await fetch(
        selectedId ? `${API}/api/services/${selectedId}` : `${API}/api/services`,
        {
          method: selectedId ? "PUT" : "POST",
          credentials: "include",
          headers: {
            "Content-Type": "application/json",
            "X-XSRF-TOKEN": readCookie("XSRF-TOKEN"),
          },
          body: JSON.stringify(payload),
        },
      )

      if (response.ok) {
        await loadServices()
        resetForm()
        setFormVisible(false)
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

  function describePrice(service: Service): string {
    return service.pricePublic && service.priceAmount !== null
      ? brl.format(service.priceAmount)
      : "Sem preço público"
  }

  return (
    <div className="min-h-screen bg-paper px-8 py-10 min-[960px]:px-16 min-[960px]:py-14">
      <div className="w-full max-w-[440px] text-left">
        <p className="mb-2 font-sans text-sm text-ink-soft">JM Code Studio</p>
        <h1 className="mb-8 font-display text-3xl text-ink">Meus serviços</h1>

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
            <div className="mb-6 flex flex-col items-start gap-3">
              <Button type="button" onClick={handleNew}>
                Novo serviço
              </Button>
              {listError && (
                <p role="alert" className="text-sm text-danger">
                  {listError}
                </p>
              )}
            </div>

            {services.length === 0 ? (
              <p className="mb-10 text-sm text-ink-soft">Nenhum serviço cadastrado.</p>
            ) : (
              <ul className="mb-10 flex flex-col gap-4">
                {services.map((service) => (
                  <li key={service.id} className="flex flex-col gap-2 border-b border-ink-soft/30 pb-4">
                    <p className="text-ink">{service.name}</p>
                    <p className="text-sm text-ink-soft">
                      {service.durationMinutes} min · {describePrice(service)} ·{" "}
                      {service.active ? "Ativo" : "Inativo"}
                    </p>
                    <div className="flex gap-3">
                      <Button type="button" variant="secondary" onClick={() => handleEdit(service)}>
                        Editar
                      </Button>
                      <Button
                        type="button"
                        variant="secondary"
                        onClick={() => handleToggleActive(service)}
                      >
                        {service.active ? "Desativar" : "Ativar"}
                      </Button>
                    </div>
                  </li>
                ))}
              </ul>
            )}

            {formVisible && (
              <form onSubmit={handleSubmit} className="flex flex-col gap-6">
                <h2 className="font-display text-2xl text-ink">
                  {selectedId ? "Editar serviço" : "Novo serviço"}
                </h2>

                <div className="flex flex-col gap-2">
                  <Label htmlFor="service-name">Nome</Label>
                  <AFFrame>
                    <Input
                      id="service-name"
                      type="text"
                      className="af-frame-input"
                      required
                      maxLength={150}
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      aria-describedby={formError ? "form-error" : undefined}
                    />
                  </AFFrame>
                </div>

                <div className="flex flex-col gap-2">
                  <Label htmlFor="service-description">Descrição (opcional)</Label>
                  <AFFrame>
                    <textarea
                      id="service-description"
                      className="af-frame-input w-full bg-transparent"
                      rows={3}
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                      aria-describedby={formError ? "form-error" : undefined}
                    />
                  </AFFrame>
                </div>

                <div className="flex flex-col gap-2">
                  <Label htmlFor="service-duration">Duração em minutos</Label>
                  <AFFrame>
                    <Input
                      id="service-duration"
                      type="number"
                      className="af-frame-input"
                      required
                      min={1}
                      step={1}
                      value={durationMinutes}
                      onChange={(e) => setDurationMinutes(e.target.value)}
                      aria-describedby={formError ? "form-error" : undefined}
                    />
                  </AFFrame>
                </div>

                <div className="flex flex-col gap-2">
                  <Label htmlFor="service-price">Preço em reais (opcional)</Label>
                  <AFFrame>
                    <Input
                      id="service-price"
                      type="number"
                      className="af-frame-input"
                      min={0}
                      step="0.01"
                      value={priceAmount}
                      onChange={(e) => handlePriceChange(e.target.value)}
                      aria-describedby={formError ? "form-error" : undefined}
                    />
                  </AFFrame>
                </div>

                <div className="flex items-center gap-2">
                  <input
                    id="service-price-public"
                    type="checkbox"
                    checked={pricePublic}
                    disabled={priceAmount.trim() === ""}
                    onChange={(e) => setPricePublic(e.target.checked)}
                  />
                  <Label htmlFor="service-price-public">Mostrar preço publicamente</Label>
                </div>

                <div className="flex flex-col gap-2">
                  <Label htmlFor="service-notice">Antecedência mínima em minutos</Label>
                  <AFFrame>
                    <Input
                      id="service-notice"
                      type="number"
                      className="af-frame-input"
                      required
                      min={0}
                      step={1}
                      value={minimumNoticeMinutes}
                      onChange={(e) => setMinimumNoticeMinutes(e.target.value)}
                      aria-describedby={formError ? "form-error" : undefined}
                    />
                  </AFFrame>
                </div>

                <div className="flex flex-col gap-2">
                  <Label htmlFor="service-buffer">Intervalo após o ensaio em minutos</Label>
                  <AFFrame>
                    <Input
                      id="service-buffer"
                      type="number"
                      className="af-frame-input"
                      required
                      min={0}
                      step={1}
                      value={bufferAfterMinutes}
                      onChange={(e) => setBufferAfterMinutes(e.target.value)}
                      aria-describedby={formError ? "form-error" : undefined}
                    />
                  </AFFrame>
                </div>

                <div className="flex flex-col gap-3">
                  <Button type="submit" disabled={submitting}>
                    {submitting ? "Salvando…" : "Salvar"}
                  </Button>
                  {formError && (
                    <p id="form-error" role="alert" className="text-sm text-danger">
                      {formError}
                    </p>
                  )}
                </div>
              </form>
            )}

            <p className="mt-6 text-sm text-ink-soft">
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
