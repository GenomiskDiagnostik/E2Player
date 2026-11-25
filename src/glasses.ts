import {
  CreateStartUpPageContainer,
  EventSourceType,
  OsEventTypeList,
  RebuildPageContainer,
  StartUpPageCreateResult,
  TextContainerProperty,
  TextContainerUpgrade,
  waitForEvenAppBridge,
} from '@evenrealities/even_hub_sdk'
import { Playback, commandLabels, commands, formatTime, wrapIndex } from './playback.ts'

const containers = [
  { containerID: 1, containerName: 'playing', yPosition: 12, height: 62, isEventCapture: 0 },
  { containerID: 2, containerName: 'controls', yPosition: 76, height: 142, isEventCapture: 1 },
  { containerID: 3, containerName: 'caption', yPosition: 221, height: 59, isEventCapture: 0 },
]
const inputContainer = containers[1]
const touchSources = [
  EventSourceType.TOUCH_EVENT_FROM_GLASSES_R,
  EventSourceType.TOUCH_EVENT_FROM_GLASSES_L,
  EventSourceType.TOUCH_EVENT_FROM_RING,
]

export async function connectGlasses(playback: Playback): Promise<void> {
  const bridge = await waitForEvenAppBridge()
  let selected = commands.indexOf('togglePlayback')

  function lines(): string[] {
    const snapshot = playback.snapshot
    const labels = commandLabels(snapshot)
    return [
      `${snapshot.track.title}\n${snapshot.status}   ${formatTime(snapshot.time)} / ${formatTime(snapshot.duration)}`,
      commands.map((command, index) => `${index === selected ? '> ' : '  '}${labels[command]}`).join('\n'),
      snapshot.error || snapshot.caption || ' ',
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
    switch (type) {
      case OsEventTypeList.CLICK_EVENT:
      case undefined:
        playback.run(commands[selected])
        return
      case OsEventTypeList.SCROLL_TOP_EVENT:
        selected = wrapIndex(selected - 1, commands.length)
        break
      case OsEventTypeList.SCROLL_BOTTOM_EVENT:
        selected = wrapIndex(selected + 1, commands.length)
        break
      default:
        return
    }
    void updateDisplay()
  })
  playback.subscribe(() => void updateDisplay())
  console.info('[E2Player] Glasses page ready')
}
