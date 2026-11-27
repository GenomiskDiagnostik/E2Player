const bridgeUrl = 'http://127.0.0.1:47841'
const socketUrls = ['ws://localhost:47842/v1/bridge', 'ws://127.0.0.1:47842/v1/bridge']
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
type Session = { code: string; timer?: ReturnType<typeof setTimeout>; abort: AbortController; transport: number }
type Method = 'GET' | 'POST'
type Transport = (code: string, path: string, method: Method, signal: AbortSignal) => Promise<Response>

function socketTransport(Socket: typeof WebSocket, url: string): Transport {
  return (code, path, method, signal) => new Promise((resolve, reject) => {
    signal.throwIfAborted()
    const socket = new Socket(url)
    function close(): void {
      signal.removeEventListener('abort', fail)
      socket.close()
    }
    function fail(): void {
      reject(new Error('Local WebSocket unavailable'))
      close()
    }
    socket.addEventListener('open', () => socket.send(`${code}\n${method}\n${path}`), { once: true })
    socket.addEventListener('error', fail, { once: true })
    socket.addEventListener('close', fail, { once: true })
    signal.addEventListener('abort', fail, { once: true })
    socket.addEventListener('message', ({ data }) => {
      if (typeof data !== 'string' || data.length > 4100 || !/^(200|204|400|401|404|409)\n/.test(data)) {
        fail()
        return
      }
      const status = Number(data.slice(0, 3))
      resolve(new Response(status === 200 ? data.slice(4) : null, { status }))
      close()
    }, { once: true })
  })
}

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
  private readonly transports: Transport[]

  constructor(changed: (state: LinkState) => void, fetcher: typeof fetch = fetch, Socket?: typeof WebSocket) {
    this.changed = changed
    const http: Transport = (code, path, method, signal) => {
      const options: RequestInit & { targetAddressSpace: 'loopback' } = {
        method, signal, headers: { Authorization: `Bearer ${code}` }, mode: 'cors',
        credentials: 'omit', cache: 'no-store', redirect: 'error', targetAddressSpace: 'loopback',
      }
      return fetcher(bridgeUrl + path, options)
    }
    this.transports = Socket ? [...socketUrls.map(url => socketTransport(Socket, url)), http] : [http]
  }

  get state(): LinkState {
    return this.current
  }

  connect(code: string): boolean {
    if (!/^[A-Za-z0-9_-]{22}$/.test(code)) return false
    this.endSession()
    const session = { code, abort: new AbortController(), transport: 0 }
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

  private request(session: Session, path: string, method: Method): Promise<Response> {
    const signal = AbortSignal.any([session.abort.signal, AbortSignal.timeout(1300)])
    return this.transports[session.transport](session.code, path, method, signal)
  }

  private async read(session: Session): Promise<Response> {
    for (let attempt = 0; attempt < this.transports.length; attempt++) {
      try {
        return await this.request(session, '/v1/state', 'GET')
      } catch (error) {
        if (session.abort.signal.aborted || attempt + 1 === this.transports.length) throw error
        session.transport = (session.transport + 1) % this.transports.length
      }
    }
    throw new Error('No bridge transport')
  }

  private async poll(session: Session): Promise<void> {
    let delay = 350
    try {
      const response = await this.read(session)
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
