import './style.css'
import { CaptionLink, type MediaCommand } from './caption-link.ts'
import { demoTracks } from './demo.ts'
import { connectGlasses, type GlassesControl, type GlassesScreen } from './glasses.ts'
import {
  languageNames, messages, readLanguagePreference, resolveLocale, saveLanguagePreference,
  type LanguagePreference, type Message,
} from './i18n.ts'
import { Playback, formatTime, type Command } from './playback.ts'

const element = <T extends Element = HTMLElement>(selector: string) => document.querySelector<T>(selector)!
const media = element<HTMLVideoElement>('#media')
const playback = new Playback(media, demoTracks)
const seek = element<HTMLInputElement>('#seek')
const language = element<HTMLSelectElement>('#language')
const pairing = element<HTMLFormElement>('#pairing')
const code = element<HTMLInputElement>('#pairing-code')
const pairingError = element('#pairing-error')
const androidPanel = element('#android-panel')
const demoPanel = element('#demo-panel')
const androidSource = element('#android-source')
const demoSource = element('#demo-source')
const captions = element('#caption')
const controls = element('#controls')
const disconnect = element('#disconnect')

let source: GlassesScreen['source'] = 'android'
let device: Message = 'phonePreview'
let preference = readLanguagePreference()
let t = messages(resolveLocale(preference, navigator.languages))
let showPhoneCaptions = true
let commandFailed = false
let refreshGlasses: (() => void) | undefined
const link = new CaptionLink(() => {
  if (source === 'android') render()
}, fetch, globalThis.WebSocket)

const transport = ['previous', 'play', 'stop', 'next', 'captions'] as const
type TransportIcon = typeof transport[number] | 'pause'
type Control = GlassesControl & { icon: TransportIcon; pressed?: boolean }
const statuses = { Stopped: 'stopped', Paused: 'paused', Playing: 'playing', Starting: 'starting' } as const

function toggleCaptions(): void {
  if (source === 'demo') playback.run('toggleCaptions')
  else {
    showPhoneCaptions = !showPhoneCaptions
    render()
  }
}

function getControls(): Control[] {
  const snapshot = playback.snapshot
  const phone = link.state.media
  const demo = source === 'demo'
  const isPlaying = demo ? snapshot.status === 'Playing' || snapshot.status === 'Starting' : phone?.playing
  const captionsOn = demo ? snapshot.captionsEnabled : showPhoneCaptions
  const playAction = isPlaying ? 'pause' : 'play'

  function mediaAction(icon: MediaCommand, symbol: string, demoCommand: Command): Control {
    return {
      icon, symbol, label: t[icon], enabled: demo || Boolean(phone?.actions.includes(icon)),
      run: () => {
        if (demo) playback.run(demoCommand)
        else void link.send(icon).then(success => {
          commandFailed = !success
          if (source === 'android') render()
        })
      },
    }
  }

  return [
    mediaAction('previous', '|<', 'previous'),
    mediaAction(playAction, isPlaying ? '||' : '>', 'togglePlayback'),
    mediaAction('stop', '[]', 'stop'),
    mediaAction('next', '>|', 'next'),
    { icon: 'captions', symbol: captionsOn ? 'CC+' : 'CC-', label: captionsOn ? t.captionsOn : t.captionsOff,
      enabled: true, run: toggleCaptions, pressed: captionsOn },
  ]
}

const buttons = transport.map((name, index) => {
  const button = document.createElement('button')
  button.type = 'button'
  const icon = document.createElement('span')
  icon.className = 'transport-icon'
  icon.setAttribute('aria-hidden', 'true')
  if (name === 'captions') icon.textContent = 'CC'
  button.append(icon)
  button.addEventListener('click', () => {
    const action = getControls()[index]
    if (action.enabled) action.run()
  })
  controls.append(button)
  return button
})

function setText(target: Element, value: string): void {
  if (target.textContent !== value) target.textContent = value
}

function demoTitle(): string {
  return playback.snapshot.track.kind === 'video' ? t.testPattern : t.descendingTones
}

function liveCaption(): string {
  return source === 'demo' ? playback.snapshot.caption : showPhoneCaptions ? link.state.caption : ''
}

function phoneTime(): string {
  const phone = link.state.media
  if (!phone || phone.positionMs < 0) return ''
  const position = formatTime(phone.positionMs / 1000)
  return phone.durationMs > 0 ? `${position} / ${formatTime(phone.durationMs / 1000)}` : position
}

function readGlasses(): GlassesScreen {
  const phone = link.state.media
  const snapshot = playback.snapshot
  const status = link.state.status
  const androidStatus = status === 'connected' ? phoneTime() || t.connected
    : status === 'connecting' ? t.connecting : t.pairPrompt
  const heading = source === 'android'
    ? `${phone?.title || t.phoneMedia}\n${androidStatus}`
    : `${demoTitle()}\n${t[statuses[snapshot.status]]}  ${formatTime(snapshot.time)} / ${formatTime(snapshot.duration)}`
  return { source, heading, controls: getControls(),
    caption: source === 'demo' && snapshot.error ? t[snapshot.error] : liveCaption() }
}

function render(): void {
  const android = source === 'android'
  const state = link.state
  const snapshot = playback.snapshot
  setText(element('#device'), t[device])
  androidPanel.hidden = !android
  demoPanel.hidden = android
  androidSource.setAttribute('aria-pressed', String(android))
  demoSource.setAttribute('aria-pressed', String(!android))

  const paired = state.status === 'connecting' || state.status === 'connected' || state.status === 'unavailable'
  pairing.hidden = paired
  disconnect.hidden = !paired
  element('#link-help').hidden = paired
  setText(element('#android-title'), state.media?.title || t.phoneMedia)
  const linkStatus = {
    unpaired: t.pairPrompt, connecting: t.connecting,
    connected: commandFailed ? t.commandFailed : state.caption ? phoneTime() || t.connected : t.waitingCaption,
    unavailable: t.unavailable, rejected: t.rejected,
  }
  setText(element('#link-status'), linkStatus[state.status])
  const mediaHelp = element('#media-help')
  mediaHelp.hidden = state.status !== 'connected' || state.media !== null
  setText(mediaHelp, state.mediaAccess ? t.noSession : t.mediaHelp)

  if (!android) {
    const audio = snapshot.track.kind === 'audio'
    media.hidden = audio
    media.setAttribute('aria-label', demoTitle())
    element('#audio-label').hidden = !audio
    element('#stage').dataset.kind = snapshot.track.kind
    setText(element('#title'), demoTitle())
    setText(element('#status'), snapshot.error ? t[snapshot.error] : t[statuses[snapshot.status]])
    setText(element('#time'), `${formatTime(snapshot.time)} / ${formatTime(snapshot.duration)}`)
    seek.disabled = snapshot.duration <= 0
    seek.max = String(snapshot.duration)
    seek.value = String(snapshot.time)
  }

  const text = liveCaption()
  setText(captions, text)
  captions.hidden = !text
  getControls().forEach((control, index) => {
    const button = buttons[index]
    button.dataset.icon = control.icon
    button.disabled = !control.enabled
    button.title = control.label
    button.setAttribute('aria-label', control.label)
    if (control.pressed !== undefined) button.setAttribute('aria-pressed', String(control.pressed))
  })
  refreshGlasses?.()
}

function applyLanguage(): void {
  const locale = resolveLocale(preference, navigator.languages)
  t = messages(locale)
  document.documentElement.lang = locale === 'zh' ? 'zh-Hans' : locale
  document.querySelectorAll<HTMLElement>('[data-text]').forEach(target => setText(target, t[target.dataset.text as Message]))
  controls.setAttribute('aria-label', t.playbackControls)
  element('#sources').setAttribute('aria-label', t.source)
  seek.setAttribute('aria-label', t.position)
  if (!pairingError.hidden) setText(pairingError, t.wrongCode)
  render()
}

function setSource(next: GlassesScreen['source']): void {
  if (source === next) return
  source = next
  if (source === 'android') playback.run('stop')
  render()
}

for (const [tag, name] of Object.entries(languageNames)) language.add(new Option(name, tag))
language.value = preference
language.addEventListener('change', () => {
  preference = language.value as LanguagePreference
  saveLanguagePreference(preference)
  applyLanguage()
})
window.addEventListener('languagechange', applyLanguage)
window.addEventListener('pagehide', () => link.disconnect())
androidSource.addEventListener('click', () => setSource('android'))
demoSource.addEventListener('click', () => setSource('demo'))
seek.addEventListener('input', () => playback.seek(seek.valueAsNumber))
code.addEventListener('input', () => { pairingError.hidden = true })
pairing.addEventListener('submit', event => {
  event.preventDefault()
  commandFailed = false
  const valid = link.connect(code.value.trim())
  setText(pairingError, t.wrongCode)
  pairingError.hidden = valid
  if (valid) code.value = ''
})
disconnect.addEventListener('click', () => link.disconnect())

playback.subscribe(() => { if (source === 'demo') render() })
applyLanguage()
void connectGlasses(readGlasses).then(refresh => {
  refreshGlasses = refresh
  device = 'hubConnected'
  render()
}).catch(error => {
  device = 'hubError'
  render()
  console.error('[E2Player] Glasses setup failed', error)
})
