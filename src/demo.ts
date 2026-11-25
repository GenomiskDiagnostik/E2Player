import audioUrl from './media/descending-tones.mp3?url'
import videoUrl from './media/test-pattern.mp4?url'
import type { Track } from './playback.ts'

export const demoTracks = [
  {
    title: 'Test pattern',
    kind: 'video',
    src: videoUrl,
    cues: [
      { start: 0, end: 4, text: '[Moving test pattern and low tone]' },
      { start: 4, end: 8, text: '[The pitch rises]' },
      { start: 8, end: 12, text: '[A higher tone plays]' },
    ],
  },
  {
    title: 'Descending tones',
    kind: 'audio',
    src: audioUrl,
    cues: [
      { start: 0, end: 4, text: '[A high tone plays]' },
      { start: 4, end: 8, text: '[The pitch drops]' },
      { start: 8, end: 12, text: '[A lower tone plays]' },
    ],
  },
] as const satisfies readonly [Track, ...Track[]]
