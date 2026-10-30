import { useCallback, useEffect, useState, type FormEvent } from "react"

import { AFFrame } from "@/components/ui/af-frame"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

const NETWORK_ERROR = "Não foi possível conectar. Confira sua internet e tente de novo."
const GENERIC_ERROR = "Não foi possível salvar. Tente de novo."
const LOAD_ERROR = "Não foi possível carregar a agenda. Tente de novo."
const SESSION_EXPIRED = "Sua sessão expirou. Volte e entre de novo."

const API = import.meta.env.VITE_API_BASE_URL

const WEEKDAY_LABELS = [
  "Domingo",
  "Segunda-feira",
  "Terça-feira",
  "Quarta-feira",
  "Quinta-feira",
  "Sexta-feira",
  "Sábado",
]

type WeeklyRange = {
  key: string
  weekday: number
  startTime: string
  endTime: string
}

type CalendarBlock = {
  id: string
  blockType: "TIME_OFF" | "MANUAL_BLOCK"
  startsAt: string
  endsAt: string
  reason: string | null
}

type AgendaSettings = {
  timezone: string
  bookingHorizonDate: string | null
  weeklySchedule: { weekday: number; startTime: string; endTime: string }[]
  calendarBlocks: CalendarBlock[]
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

function postHeaders() {
  return {
    "Content-Type": "application/json",
    "X-XSRF-TOKEN": readCookie("XSRF-TOKEN"),
  }
}

function newKey(): string {
  return Math.random().toString(36).slice(2)
}

// Converte o valor de um <input type="datetime-local"> (hora de parede, sem
// offset) para o instante correspondente no fuso IANA do perfil do
// fotógrafo — não no fuso do navegador de quem está preenchendo o
// formulário. Corrige um desvio da RN-009 (tudo deve ser interpretado no
// fuso configurado pelo fotógrafo): sem isto, uma fotógrafa com perfil em
// America/Manaus configurando a agenda de um notebook em America/Sao_Paulo
// gravaria um horário diferente do que vê na própria tela.
//
// Técnica: formata o "palpite UTC" (tratar a hora de parede como se já
// fosse UTC) como se estivesse no fuso de destino, mede a diferença entre
// os dois, e aplica essa diferença de volta ao palpite. Funciona de forma
// exata para fusos sem transição de horário de verão no instante usado —
// o caso do Brasil desde 2019 (ver pendência de DST documentada para a
// Fase 3.2 em claude/fase-3.1-configuracao-agenda.md).
function localInputToOffsetDateTime(value: string, timeZone: string): string | null {
  if (!value) return null

  const utcGuess = new Date(value + "Z")
  if (Number.isNaN(utcGuess.getTime())) return null
  if (!timeZone) return utcGuess.toISOString()

  const asIfInZone = new Date(utcGuess.toLocaleString("en-US", { timeZone }))
  const offsetMs = utcGuess.getTime() - asIfInZone.getTime()
  return new Date(utcGuess.getTime() + offsetMs).toISOString()
}

function formatBlockPeriod(block: CalendarBlock): string {
  const start = new Date(block.startsAt)
  const end = new Date(block.endsAt)
  const fmt = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" })
  return `${fmt.format(start)} até ${fmt.format(end)}`
}

type AgendaPageProps = {
  onBack: () => void
}

export default function AgendaPage({ onBack }: AgendaPageProps) {
  const [loading, setLoading] = useState(true)
  const [sessionExpired, setSessionExpired] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [timezone, setTimezone] = useState("")

  const [ranges, setRanges] = useState<WeeklyRange[]>([])
  const [scheduleSubmitting, setScheduleSubmitting] = useState(false)
  const [scheduleError, setScheduleError] = useState<string | null>(null)
  const [scheduleSuccess, setScheduleSuccess] = useState(false)

  const [blocks, setBlocks] = useState<CalendarBlock[]>([])
  const [blockType, setBlockType] = useState<"TIME_OFF" | "MANUAL_BLOCK">("TIME_OFF")
  const [blockStart, setBlockStart] = useState("")
  const [blockEnd, setBlockEnd] = useState("")
  const [blockReason, setBlockReason] = useState("")
  const [blockSubmitting, setBlockSubmitting] = useState(false)
  const [blockError, setBlockError] = useState<string | null>(null)

  const [horizonDate, setHorizonDate] = useState("")
  const [horizonSubmitting, setHorizonSubmitting] = useState(false)
  const [horizonError, setHorizonError] = useState<string | null>(null)
  const [horizonSuccess, setHorizonSuccess] = useState(false)

  const loadSettings = useCallback(async (): Promise<boolean> => {
    try {
      const response = await fetch(`${API}/api/agenda`, { credentials: "include" })

      if (response.status === 401) {
        setSessionExpired(true)
        return false
      }
      if (response.ok) {
        const settings: AgendaSettings = await response.json()
        setTimezone(settings.timezone)
        setHorizonDate(settings.bookingHorizonDate ?? "")
        setRanges(settings.weeklySchedule.map((r) => ({ ...r, key: newKey() })))
        setBlocks(settings.calendarBlocks)
        setLoadError(null)
        return true
      }
      setLoadError((await readMessage(response)) ?? LOAD_ERROR)
    } catch {
      setLoadError(NETWORK_ERROR)
    }
    return false
  }, [])

  useEffect(() => {
    let cancelled = false

    async function load() {
      await loadSettings()
      if (!cancelled) setLoading(false)
    }

    load()
    return () => {
      cancelled = true
    }
  }, [loadSettings])

  function addRange(weekday: number) {
    setRanges((current) => [...current, { key: newKey(), weekday, startTime: "09:00", endTime: "18:00" }])
    setScheduleSuccess(false)
  }

  function removeRange(key: string) {
    setRanges((current) => current.filter((r) => r.key !== key))
    setScheduleSuccess(false)
  }

  function updateRange(key: string, field: "startTime" | "endTime", value: string) {
    setRanges((current) => current.map((r) => (r.key === key ? { ...r, [field]: value } : r)))
    setScheduleSuccess(false)
  }

  async function handleSaveSchedule() {
    setScheduleSubmitting(true)
    setScheduleError(null)
    setScheduleSuccess(false)

    try {
      const response = await fetch(`${API}/api/agenda/weekly-schedule`, {
        method: "PUT",
        credentials: "include",
        headers: postHeaders(),
        body: JSON.stringify({
          ranges: ranges.map((r) => ({ weekday: r.weekday, startTime: r.startTime, endTime: r.endTime })),
        }),
      })

      if (response.ok) {
        const saved = await response.json()
        setRanges(saved.map((r: { weekday: number; startTime: string; endTime: string }) => ({ ...r, key: newKey() })))
        setScheduleSuccess(true)
      } else if (response.status === 401) {
        setSessionExpired(true)
      } else {
        setScheduleError((await readMessage(response)) ?? GENERIC_ERROR)
      }
    } catch {
      setScheduleError(NETWORK_ERROR)
    } finally {
      setScheduleSubmitting(false)
    }
  }

  async function handleAddBlock(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBlockSubmitting(true)
    setBlockError(null)

    const startsAt = localInputToOffsetDateTime(blockStart, timezone)
    const endsAt = localInputToOffsetDateTime(blockEnd, timezone)

    try {
      const response = await fetch(`${API}/api/agenda/blocks`, {
        method: "POST",
        credentials: "include",
        headers: postHeaders(),
        body: JSON.stringify({
          blockType,
          startsAt,
          endsAt,
          reason: blockReason.trim() === "" ? null : blockReason,
        }),
      })

      if (response.ok) {
        const created: CalendarBlock = await response.json()
        setBlocks((current) => [...current, created].sort((a, b) => a.startsAt.localeCompare(b.startsAt)))
        setBlockStart("")
        setBlockEnd("")
        setBlockReason("")
      } else if (response.status === 401) {
        setSessionExpired(true)
      } else {
        setBlockError((await readMessage(response)) ?? GENERIC_ERROR)
      }
    } catch {
      setBlockError(NETWORK_ERROR)
    } finally {
      setBlockSubmitting(false)
    }
  }

  async function handleDeleteBlock(id: string) {
    setBlockError(null)

    try {
      const response = await fetch(`${API}/api/agenda/blocks/${id}`, {
        method: "DELETE",
        credentials: "include",
        headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") },
      })

      if (response.ok || response.status === 204) {
        setBlocks((current) => current.filter((b) => b.id !== id))
      } else if (response.status === 401) {
        setSessionExpired(true)
      } else {
        setBlockError((await readMessage(response)) ?? GENERIC_ERROR)
      }
    } catch {
      setBlockError(NETWORK_ERROR)
    }
  }

  async function handleSaveHorizon(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setHorizonSubmitting(true)
    setHorizonError(null)
    setHorizonSuccess(false)

    try {
      const response = await fetch(`${API}/api/agenda/booking-horizon`, {
        method: "PUT",
        credentials: "include",
        headers: postHeaders(),
        body: JSON.stringify({ bookingHorizonDate: horizonDate.trim() === "" ? null : horizonDate }),
      })

      if (response.ok) {
        setHorizonSuccess(true)
      } else if (response.status === 401) {
        setSessionExpired(true)
      } else {
        setHorizonError((await readMessage(response)) ?? GENERIC_ERROR)
      }
    } catch {
      setHorizonError(NETWORK_ERROR)
    } finally {
      setHorizonSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen bg-paper px-8 py-10 min-[960px]:px-16 min-[960px]:py-14">
      <div className="w-full max-w-[560px] text-left">
        <p className="mb-2 font-sans text-sm text-ink-soft">JM Code Studio</p>
        <h1 className="mb-8 font-display text-3xl text-ink">Configurar agenda</h1>

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
            {loadError && (
              <p role="alert" className="mb-6 text-sm text-danger">
                {loadError}
              </p>
            )}

            {timezone && <p className="mb-10 text-sm text-ink-soft">Fuso horário da agenda: {timezone}</p>}

            <section className="mb-12 flex flex-col gap-6">
              <h2 className="font-display text-2xl text-ink">Jornada semanal</h2>

              {WEEKDAY_LABELS.map((label, weekday) => {
                const dayRanges = ranges.filter((r) => r.weekday === weekday)
                return (
                  <div key={weekday} className="flex flex-col gap-3 border-b border-ink-soft/30 pb-4">
                    <p className="text-ink">{label}</p>

                    {dayRanges.length === 0 && <p className="text-sm text-ink-soft">Sem atendimento</p>}

                    {dayRanges.map((range) => (
                      <div key={range.key} className="flex flex-wrap items-center gap-3">
                        <AFFrame>
                          <Input
                            type="time"
                            className="af-frame-input"
                            value={range.startTime}
                            onChange={(e) => updateRange(range.key, "startTime", e.target.value)}
                          />
                        </AFFrame>
                        <span className="text-sm text-ink-soft">até</span>
                        <AFFrame>
                          <Input
                            type="time"
                            className="af-frame-input"
                            value={range.endTime}
                            onChange={(e) => updateRange(range.key, "endTime", e.target.value)}
                          />
                        </AFFrame>
                        <Button type="button" variant="secondary" onClick={() => removeRange(range.key)}>
                          Remover
                        </Button>
                      </div>
                    ))}

                    <Button type="button" variant="secondary" onClick={() => addRange(weekday)}>
                      Adicionar faixa
                    </Button>
                  </div>
                )
              })}

              <p className="text-sm text-ink-soft">
                Uma faixa com término antes ou igual ao início é entendida como atravessando a meia-noite e
                é dividida automaticamente entre os dois dias ao salvar.
              </p>

              <div className="flex flex-col items-start gap-3">
                <Button type="button" disabled={scheduleSubmitting} onClick={handleSaveSchedule}>
                  {scheduleSubmitting ? "Salvando…" : "Salvar jornada"}
                </Button>
                {scheduleSuccess && <p className="text-sm text-ink-soft">Jornada salva.</p>}
                {scheduleError && (
                  <p role="alert" className="text-sm text-danger">
                    {scheduleError}
                  </p>
                )}
              </div>
            </section>

            <section className="mb-12 flex flex-col gap-6">
              <h2 className="font-display text-2xl text-ink">Folgas e bloqueios</h2>

              {blocks.length === 0 ? (
                <p className="text-sm text-ink-soft">Nenhuma folga ou bloqueio cadastrado.</p>
              ) : (
                <ul className="flex flex-col gap-4">
                  {blocks.map((block) => (
                    <li key={block.id} className="flex flex-col gap-2 border-b border-ink-soft/30 pb-4">
                      <p className="text-ink">{block.blockType === "TIME_OFF" ? "Folga" : "Bloqueio manual"}</p>
                      <p className="text-sm text-ink-soft">{formatBlockPeriod(block)}</p>
                      {block.reason && <p className="text-sm text-ink-soft">{block.reason}</p>}
                      <div>
                        <Button type="button" variant="secondary" onClick={() => handleDeleteBlock(block.id)}>
                          Remover
                        </Button>
                      </div>
                    </li>
                  ))}
                </ul>
              )}

              <form onSubmit={handleAddBlock} className="flex flex-col gap-4">
                <div className="flex flex-col gap-2">
                  <Label htmlFor="block-type">Tipo</Label>
                  <AFFrame>
                    <select
                      id="block-type"
                      className="af-frame-input w-full bg-transparent"
                      value={blockType}
                      onChange={(e) => setBlockType(e.target.value as "TIME_OFF" | "MANUAL_BLOCK")}
                    >
                      <option value="TIME_OFF">Folga</option>
                      <option value="MANUAL_BLOCK">Bloqueio manual</option>
                    </select>
                  </AFFrame>
                </div>

                <div className="flex flex-col gap-2">
                  <Label htmlFor="block-start">Início</Label>
                  <AFFrame>
                    <Input
                      id="block-start"
                      type="datetime-local"
                      className="af-frame-input"
                      required
                      value={blockStart}
                      onChange={(e) => setBlockStart(e.target.value)}
                    />
                  </AFFrame>
                </div>

                <div className="flex flex-col gap-2">
                  <Label htmlFor="block-end">Término</Label>
                  <AFFrame>
                    <Input
                      id="block-end"
                      type="datetime-local"
                      className="af-frame-input"
                      required
                      value={blockEnd}
                      onChange={(e) => setBlockEnd(e.target.value)}
                    />
                  </AFFrame>
                </div>

                <div className="flex flex-col gap-2">
                  <Label htmlFor="block-reason">Motivo (opcional)</Label>
                  <AFFrame>
                    <Input
                      id="block-reason"
                      type="text"
                      className="af-frame-input"
                      value={blockReason}
                      onChange={(e) => setBlockReason(e.target.value)}
                    />
                  </AFFrame>
                </div>

                <div className="flex flex-col items-start gap-3">
                  <Button type="submit" disabled={blockSubmitting}>
                    {blockSubmitting ? "Adicionando…" : "Adicionar"}
                  </Button>
                  {blockError && (
                    <p role="alert" className="text-sm text-danger">
                      {blockError}
                    </p>
                  )}
                </div>
              </form>
            </section>

            <section className="mb-10 flex flex-col gap-4">
              <h2 className="font-display text-2xl text-ink">Horizonte de reservas</h2>
              <p className="text-sm text-ink-soft">
                Data limite até onde sua agenda fica aberta para novas solicitações. Deixe em branco para não
                definir um limite.
              </p>

              <form onSubmit={handleSaveHorizon} className="flex flex-col gap-4">
                <div className="flex flex-col gap-2">
                  <Label htmlFor="horizon-date">Data limite</Label>
                  <AFFrame>
                    <Input
                      id="horizon-date"
                      type="date"
                      className="af-frame-input"
                      value={horizonDate}
                      onChange={(e) => {
                        setHorizonDate(e.target.value)
                        setHorizonSuccess(false)
                      }}
                    />
                  </AFFrame>
                </div>

                <div className="flex flex-col items-start gap-3">
                  <Button type="submit" disabled={horizonSubmitting}>
                    {horizonSubmitting ? "Salvando…" : "Salvar horizonte"}
                  </Button>
                  {horizonSuccess && <p className="text-sm text-ink-soft">Horizonte salvo.</p>}
                  {horizonError && (
                    <p role="alert" className="text-sm text-danger">
                      {horizonError}
                    </p>
                  )}
                </div>
              </form>
            </section>

            <p className="mt-6 text-sm text-ink-soft">
              <button type="button" onClick={onBack} className="text-ink underline underline-offset-2">
                Voltar
              </button>
            </p>
          </>
        )}
      </div>
    </div>
  )
}
