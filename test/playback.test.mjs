import assert from 'node:assert/strict'
import { test } from 'node:test'
import { Playback } from '../src/playback.ts'

const tracks = [
  { title: 'Video', kind: 'video', src: 'video.mp4', cues: [
    { start: 0, end: 4, text: 'First' },
    { start: 4, end: 8, text: 'Second' },
  ] },
  { title: 'Audio', kind: 'audio', src: 'audio.mp3', cues: [
    { start: 0, end: 4, text: 'Audio cue' },
  ] },
]

class TestMedia extends EventTarget {
  src = ''
  currentTime = 0
  duration = 12
  paused = true
  ended = false
  readyState = 1

  load() {
    this.currentTime = 0
    this.paused = true
    this.ended = false
  }

  pause() {
    this.paused = true
  }

  async play() {
    this.paused = false
  }
}

test('play, pause and stop keep the caption aligned to the playback position', async () => {
  const player = new Playback(new TestMedia(), tracks)
  player.run('toggleCaptions')
  assert.equal(player.snapshot.caption, '')

  player.run('togglePlayback')
  await Promise.resolve()
  assert.equal(player.snapshot.status, 'Playing')
  assert.equal(player.snapshot.caption, 'First')
  player.seek(4)
  assert.equal(player.snapshot.caption, 'Second')
  player.run('togglePlayback')
  assert.equal(player.snapshot.status, 'Paused')
  assert.equal(player.snapshot.caption, 'Second')

  player.run('toggleCaptions')
  assert.equal(player.snapshot.caption, '')
  player.run('stop')
  assert.equal(player.snapshot.status, 'Stopped')
  assert.equal(player.snapshot.time, 0)
})

test('previous and next wrap and preserve the current transport state', async () => {
  const media = new TestMedia()
  const player = new Playback(media, tracks)
  player.run('previous')
  assert.equal(media.src, 'audio.mp3')
  assert.equal(player.snapshot.status, 'Stopped')
  player.run('next')
  assert.equal(media.src, 'video.mp4')

  player.run('togglePlayback')
  await Promise.resolve()
  player.seek(7)
  player.run('toggleCaptions')
  player.run('next')
  await Promise.resolve()
  assert.equal(player.snapshot.status, 'Playing')
  assert.equal(player.snapshot.time, 0)
  assert.equal(player.snapshot.caption, 'Audio cue')
})

test('normal end clears captions and rejected play offers a phone fallback', async () => {
  const media = new TestMedia()
  const player = new Playback(media, tracks)
  player.run('toggleCaptions')
  player.run('togglePlayback')
  await Promise.resolve()
  media.ended = true
  media.currentTime = 12
  assert.equal(player.snapshot.status, 'Stopped')
  assert.equal(player.snapshot.caption, '')

  media.play = async () => { throw new DOMException('Blocked', 'NotAllowedError') }
  player.run('togglePlayback')
  await Promise.resolve()
  assert.match(player.snapshot.error, /Tap Play on the phone/)
})

test('stop cancels an in-flight start and suppresses its later rejection', async () => {
  const media = new TestMedia()
  const player = new Playback(media, tracks)
  let rejectPlay
  media.play = () => new Promise((resolve, reject) => { rejectPlay = reject })

  player.run('togglePlayback')
  assert.equal(player.snapshot.status, 'Starting')
  player.run('stop')
  rejectPlay(new DOMException('Interrupted', 'AbortError'))
  await Promise.resolve()
  assert.equal(player.snapshot.status, 'Stopped')
  assert.equal(player.snapshot.error, '')
})
