export interface Cue {
  start: number
  end: number
  text: string
}

export interface Track {
  title: string
  kind: 'audio' | 'video'
  src: string
  cues: readonly Cue[]
}

export type Command = 'previous' | 'togglePlayback' | 'stop' | 'next' | 'toggleCaptions'

export interface PlaybackSnapshot {
  track: Track
  status: 'Stopped' | 'Paused' | 'Playing' | 'Starting'
  time: number
  duration: number
  captionsEnabled: boolean
  caption: string
  error: '' | 'loadError' | 'playBlocked' | 'playError'
}

export const commands: readonly Command[] = ['previous', 'togglePlayback', 'stop', 'next', 'toggleCaptions']

export function wrapIndex(index: number, count: number): number {
  return (index % count + count) % count
}

export function formatTime(seconds: number): string {
  const whole = Math.floor(seconds)
  return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, '0')}`
}

export function cueAt(cues: readonly Cue[], seconds: number): string {
  return cues.find(cue => cue.start <= seconds && seconds < cue.end)?.text ?? ''
}

export type PlaybackMedia = Pick<HTMLMediaElement,
  'src' | 'currentTime' | 'duration' | 'paused' | 'ended' | 'readyState' | 'load' | 'pause' | 'play' | 'addEventListener'>

export class Playback {
  private readonly media: PlaybackMedia
  private readonly tracks: readonly [Track, ...Track[]]
  private index = 0
  private stopped = true
  private starting = false
  private attempt = 0
  private captionsEnabled = false
  private error: PlaybackSnapshot['error'] = ''
  private readonly listeners = new Set<(snapshot: PlaybackSnapshot) => void>()

  constructor(media: PlaybackMedia, tracks: readonly [Track, ...Track[]]) {
    this.media = media
    this.tracks = tracks
    for (const type of ['playing', 'pause', 'ended', 'timeupdate', 'durationchange', 'seeked']) {
      media.addEventListener(type, () => this.publish())
    }
    media.addEventListener('error', () => {
      this.error = 'loadError'
      this.publish()
    })
    this.loadTrack()
  }

  get snapshot(): PlaybackSnapshot {
    const track = this.tracks[this.index]
    const { currentTime, duration, ended, paused, readyState } = this.media
    const time = readyState > 0 ? currentTime : 0
    const status = this.starting ? 'Starting' : ended || this.stopped ? 'Stopped' : paused ? 'Paused' : 'Playing'
    return {
      track,
      status,
      time,
      duration: Number.isFinite(duration) ? duration : 0,
      captionsEnabled: this.captionsEnabled,
      caption: this.captionsEnabled && status !== 'Stopped' ? cueAt(track.cues, time) : '',
      error: this.error,
    }
  }

  subscribe(listener: (snapshot: PlaybackSnapshot) => void): void {
    this.listeners.add(listener)
    listener(this.snapshot)
  }

  seek(seconds: number): void {
    const { duration } = this.media
    if (!Number.isFinite(seconds) || !Number.isFinite(duration)) return
    this.media.currentTime = Math.min(Math.max(seconds, 0), duration)
    if (this.media.currentTime > 0) this.stopped = false
    this.publish()
  }

  run(command: Command): void {
    switch (command) {
      case 'togglePlayback':
        if (this.wantsPlayback) this.pause()
        else void this.play()
        break
      case 'stop':
        this.stopped = true
        this.pause()
        if (this.media.readyState > 0) this.media.currentTime = 0
        this.publish()
        break
      case 'previous':
      case 'next': {
        const resume = this.wantsPlayback
        this.index = wrapIndex(this.index + (command === 'next' ? 1 : -1), this.tracks.length)
        this.loadTrack()
        if (resume) void this.play()
        break
      }
      case 'toggleCaptions':
        this.captionsEnabled = !this.captionsEnabled
        this.publish()
        break
    }
  }

  private get wantsPlayback(): boolean {
    return this.starting || (!this.media.paused && !this.media.ended)
  }

  private loadTrack(): void {
    this.attempt++
    this.starting = false
    this.stopped = true
    this.error = ''
    this.media.src = this.tracks[this.index].src
    this.media.load()
    this.publish()
  }

  private pause(): void {
    this.attempt++
    this.starting = false
    this.media.pause()
    this.publish()
  }

  private async play(): Promise<void> {
    const attempt = ++this.attempt
    this.starting = true
    this.error = ''
    this.publish()
    try {
      await this.media.play()
      if (attempt === this.attempt) this.stopped = false
    } catch (error) {
      if (attempt !== this.attempt) return
      this.error = error instanceof DOMException && error.name === 'NotAllowedError'
        ? 'playBlocked'
        : 'playError'
    } finally {
      if (attempt === this.attempt) {
        this.starting = false
        this.publish()
      }
    }
  }

  private publish(): void {
    const snapshot = this.snapshot
    for (const listener of this.listeners) listener(snapshot)
  }
}
