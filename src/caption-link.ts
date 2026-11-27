const bridgeUrl = 'http://127.0.0.1:47841'
const mediaCommands = ['previous', 'play', 'pause', 'stop', 'next'] as const
export type MediaCommand = typeof mediaCommands[number]

export interface PhoneMedia {
  id: string
  title: string
  playing: boolean
  positionMs: number
  durationMs: number
  actions: readonly MediaCommand[]
}

export interface LinkState {
  status: 'unpaired' | 'connecting' | 'connected' | 'unavailable' | 'rejected'
  caption: string
  mediaAccess: boolean
  media: PhoneMedia | null
}

const empty = { caption: '', mediaAccess: false, media: null }
type Session = { code: string; timer?: ReturnType<typeof setTimeout>; abort: AbortController }

function stateFrom(value: unknown): LinkState {
  if (!value || typeof value !== 'object') throw new Error('Invalid bridge response')
  const { caption, media, mediaAccess } = value as Record<string, unknown>
  if (typeof caption !== 'string' || typeof mediaAccess !== 'boolean') throw new Error('Invalid bridge response')
  let nowPlaying: PhoneMedia | null = null
  if (media && typeof media === 'object') {
    const item = media as Record<string, unknown>
    const actions = item.actions
    if (typeof item.id === 'string' && /^[0-9]{1,12}$/.test(item.id)
      && typeof item.title === 'string' && typeof item.playing === 'boolean'
      && typeof item.positionMs === 'number' && Number.isFinite(item.positionMs)
      && typeof item.durationMs === 'number' && Number.isFinite(item.durationMs)
      && Array.isArray(actions)) {
      nowPlaying = {
        id: item.id, title: item.title.replace(/\s+/g, ' ').trim().slice(0, 100), playing: item.playing,
        positionMs: item.positionMs, durationMs: item.durationMs,
        actions: mediaCommands.filter(action => actions.includes(action)),
      }
    }
  }
  return { status: 'connected', caption: caption.slice(0, 320), mediaAccess, media: nowPlaying }
}

export class CaptionLink {
  private session?: Session
  private current: LinkState = { status: 'unpaired', ...empty }
  private readonly changed: (state: LinkState) => void
  private readonly fetcher: typeof fetch

  constructor(changed: (state: LinkState) => void, fetcher: typeof fetch = fetch) {
    this.changed = changed
    this.fetcher = fetcher
  }

  get state(): LinkState {
    return this.current
  }

  connect(code: string): boolean {
    if (!/^[A-Za-z0-9_-]{22}$/.test(code)) return false
    this.endSession()
    const session = { code, abort: new AbortController() }
    this.session = session
    this.setState({ status: 'connecting', ...empty })
    void this.poll(session)
    return true
  }

  disconnect(): void {
    this.endSession()
    this.setState({ status: 'unpaired', ...empty })
  }

  async send(action: MediaCommand): Promise<boolean> {
    const session = this.session
    const media = this.current.media
    if (!session || !media?.actions.includes(action)) return false
    try {
      const response = await this.request(session, `/v1/command/${media.id}/${action}`, 'POST')
      return this.session === session && response.status === 204
    } catch {
      return false
    }
  }

  private endSession(): void {
    if (this.session) {
      clearTimeout(this.session.timer)
      this.session.abort.abort()
      this.session = undefined
    }
  }

  private setState(state: LinkState): void {
    this.current = state
    this.changed(state)
  }

  private request(session: Session, path: string, method: 'GET' | 'POST'): Promise<Response> {
    const options: RequestInit & { targetAddressSpace: 'loopback' } = {
      method,
      headers: { Authorization: `Bearer ${session.code}` },
      signal: AbortSignal.any([session.abort.signal, AbortSignal.timeout(1300)]),
      mode: 'cors',
      credentials: 'omit',
      cache: 'no-store',
      redirect: 'error',
      targetAddressSpace: 'loopback',
    }
    return this.fetcher(bridgeUrl + path, options)
  }

  private async poll(session: Session): Promise<void> {
    let delay = 350
    try {
      const response = await this.request(session, '/v1/state', 'GET')
      if (this.session !== session) return
      if (response.status === 401) {
        this.endSession()
        this.setState({ status: 'rejected', ...empty })
        return
      }
      if (!response.ok || Number(response.headers.get('content-length')) > 4096) {
        throw new Error('Bridge unavailable')
      }
      const data: unknown = await response.json()
      if (this.session === session) this.setState(stateFrom(data))
    } catch {
      if (this.session === session) this.setState({ status: 'unavailable', ...empty })
      delay = 1100
    }
    if (this.session === session) session.timer = setTimeout(() => void this.poll(session), delay)
  }
}
