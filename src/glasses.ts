import {
  CreateStartUpPageContainer,
  EventSourceType,
  OsEventTypeList,
  RebuildPageContainer,
  StartUpPageCreateResult,
  TextContainerProperty,
  TextContainerUpgrade,
  type EvenAppBridge,
} from '@evenrealities/even_hub_sdk'
import { wrapIndex } from './playback.ts'

export interface GlassesControl {
  label: string
  enabled: boolean
  run(): void
}

export interface GlassesScreen {
  source: 'android' | 'demo'
  heading: string
  caption: string
  subtitlesOnly: boolean
  showControls(): void
  controls: readonly GlassesControl[]
}

const containers = [
  { containerID: 1, containerName: 'playing', yPosition: 12, height: 62, isEventCapture: 0 },
  { containerID: 2, containerName: 'controls', yPosition: 208, height: 78, isEventCapture: 1 },
  { containerID: 3, containerName: 'caption', yPosition: 81, height: 122, isEventCapture: 0 },
]
const inputContainer = containers[1]
const touchSources = [
  EventSourceType.TOUCH_EVENT_FROM_GLASSES_R,
  EventSourceType.TOUCH_EVENT_FROM_GLASSES_L,
  EventSourceType.TOUCH_EVENT_FROM_RING,
]

export async function connectGlasses(bridge: EvenAppBridge, read: () => GlassesScreen): Promise<() => void> {
  let selected = -1
  let source: GlassesScreen['source'] | undefined

  function focus(screen: GlassesScreen): void {
    if (source === screen.source && screen.controls[selected]?.enabled) return
    source = screen.source
    selected = screen.controls[1]?.enabled ? 1 : screen.controls.findIndex(control => control.enabled)
  }

  function lines(): string[] {
    const screen = read()
    if (screen.subtitlesOnly) return [' ', screen.caption || ' ', ' ']
    focus(screen)
    const row = screen.controls.flatMap((control, index) => {
      if (!control.enabled) return []
      return [index === selected ? `[${control.label}]` : control.label]
    }).join('   ')
    return [
      screen.heading,
      row || ' ',
      screen.caption || ' ',
    ]
  }

  const sent = lines()
  const page = {
    containerTotalNum: containers.length,
    textObject: containers.map((container, index) => new TextContainerProperty({
      ...container,
      xPosition: 20,
      width: 536,
      paddingLength: 2,
      content: sent[index],
    })),
  }
  const result = await bridge.createStartUpPageContainer(new CreateStartUpPageContainer(page))
  if (result !== StartUpPageCreateResult.success) {
    // The simulator can retain the previous page across a Vite reload.
    const reload = import.meta.env.DEV && result === StartUpPageCreateResult.invalid
    if (!reload || !await bridge.rebuildPageContainer(new RebuildPageContainer(page))) {
      throw new Error(`Glasses page creation returned ${result}`)
    }
  }

  let updating = false
  let dirty = false

  async function updateDisplay(): Promise<void> {
    dirty = true
    if (updating) return
    updating = true
    try {
      while (dirty) {
        dirty = false
        const screen = lines()
        for (const [index, { containerID, containerName }] of containers.entries()) {
          const content = screen[index]
          if (sent[index] === content) continue
          const ok = await bridge.textContainerUpgrade(
            new TextContainerUpgrade({ containerID, containerName, content }),
          )
          if (ok) sent[index] = content
        }
      }
    } catch (error) {
      console.error('[E2Player] Glasses update failed', error)
    } finally {
      updating = false
    }
  }

  bridge.onEvenHubEvent(({ textEvent, sysEvent }) => {
    const input = textEvent?.containerID === inputContainer.containerID ? textEvent : undefined
    const type = input ? input.eventType : sysEvent?.eventType
    if (type === OsEventTypeList.DOUBLE_CLICK_EVENT) {
      void bridge.shutDownPageContainer(1)
      return
    }
    // Some hosts send taps as source-only system events because click maps to enum value zero.
    if (!input && (sysEvent?.eventSource === undefined || !touchSources.includes(sysEvent.eventSource))) return
    const screen = read()
    focus(screen)
    switch (type) {
      case OsEventTypeList.CLICK_EVENT:
      case undefined:
        if (screen.subtitlesOnly) screen.showControls()
        else if (screen.controls[selected]?.enabled) screen.controls[selected].run()
        return
      case OsEventTypeList.SCROLL_TOP_EVENT:
      case OsEventTypeList.SCROLL_BOTTOM_EVENT:
        if (screen.subtitlesOnly) return
        for (let count = 0; count < screen.controls.length; count++) {
          selected = wrapIndex(selected + (type === OsEventTypeList.SCROLL_TOP_EVENT ? -1 : 1), screen.controls.length)
          if (screen.controls[selected].enabled) break
        }
        break
      default:
        return
    }
    void updateDisplay()
  })
  void updateDisplay()
  console.info('[E2Player] Glasses page ready')
  return () => void updateDisplay()
}
