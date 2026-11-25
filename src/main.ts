import './style.css'
import { demoTracks } from './demo.ts'
import { connectGlasses } from './glasses.ts'
import { Playback, commandLabels, commands, formatTime } from './playback.ts'

const media = document.querySelector<HTMLVideoElement>('#media')!
const playback = new Playback(media, demoTracks)
const title = document.querySelector('#title')!
const status = document.querySelector('#status')!
const device = document.querySelector('#device')!
const time = document.querySelector('#time')!
const seek = document.querySelector<HTMLInputElement>('#seek')!
const caption = document.querySelector<HTMLParagraphElement>('#caption')!
const stage = document.querySelector<HTMLElement>('#stage')!
const audioLabel = document.querySelector<HTMLElement>('#audio-label')!
const controls = document.querySelector('#controls')!

const buttons = commands.map(command => {
  const button = document.createElement('button')
  button.type = 'button'
  button.dataset.command = command
  button.addEventListener('click', () => playback.run(command))
  controls.append(button)
  return button
})

function setText(element: Element, value: string): void {
  if (element.textContent !== value) element.textContent = value
}

playback.subscribe(snapshot => {
  const audio = snapshot.track.kind === 'audio'
  media.hidden = audio
  audioLabel.hidden = !audio
  stage.dataset.kind = snapshot.track.kind
  setText(title, snapshot.track.title)
  setText(status, snapshot.error || snapshot.status)
  setText(time, `${formatTime(snapshot.time)} / ${formatTime(snapshot.duration)}`)
  seek.disabled = snapshot.duration <= 0
  seek.max = String(snapshot.duration)
  seek.value = String(snapshot.time)
  setText(caption, snapshot.caption)
  caption.hidden = !snapshot.caption

  const labels = commandLabels(snapshot)
  buttons.forEach((button, index) => {
    const command = commands[index]
    setText(button, labels[command])
    if (command === 'toggleCaptions') {
      button.setAttribute('aria-label', 'Captions')
      button.setAttribute('aria-pressed', String(snapshot.captionsEnabled))
    }
  })
})

seek.addEventListener('input', () => playback.seek(seek.valueAsNumber))

void connectGlasses(playback).then(() => {
  setText(device, 'Even Hub connected')
}).catch(error => {
  setText(device, 'Could not connect to Even Hub')
  console.error('[E2Player] Glasses setup failed', error)
})
