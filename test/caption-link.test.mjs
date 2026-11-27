import assert from 'node:assert/strict'
import { setImmediate } from 'node:timers/promises'
import { test } from 'node:test'
import { CaptionLink } from '../src/caption-link.ts'
import { languageNames, messages, resolveLocale } from '../src/i18n.ts'

const code = '1234567890123456789012'

test('caption link displays text and sends only reported controls to the current session', async () => {
  const requests = []
  const link = new CaptionLink(() => {}, async (url, options) => {
    requests.push({ url, options })
    if (options.method === 'POST') return new Response(null, { status: 204 })
    return Response.json({
      caption: 'Øjeblikket er nu', mediaAccess: true,
      media: { id: '4', title: 'Test title', playing: true, positionMs: 4200, durationMs: 10000, actions: ['pause'] },
    })
  })
  try {
    assert.equal(link.connect(code), true)
    await setImmediate()
    assert.equal(link.state.status, 'connected')
    assert.equal(link.state.caption, 'Øjeblikket er nu')
    assert.equal(await link.send('stop'), false)
    assert.equal(await link.send('pause'), true)
    assert.deepEqual(requests.map(({ url }) => url), [
      'http://127.0.0.1:47841/v1/state', 'http://127.0.0.1:47841/v1/command/4/pause',
    ])
    assert.ok(requests.every(({ options }) => options.redirect === 'error'
      && options.headers.Authorization === `Bearer ${code}`))
  } finally {
    link.disconnect()
  }
  assert.equal(link.state.caption, '')
})

test('disconnect prevents pending replies from restoring a caption and rejects expired codes', async () => {
  let complete
  const delayed = new CaptionLink(() => {}, () => new Promise(resolve => { complete = resolve }))
  assert.equal(delayed.connect(code), true)
  delayed.disconnect()
  complete(Response.json({ caption: 'Old caption', mediaAccess: false, media: null }))
  await setImmediate()
  assert.equal(delayed.state.status, 'unpaired')
  assert.equal(delayed.state.caption, '')

  const expired = new CaptionLink(() => {}, async () => new Response(null, { status: 401 }))
  assert.equal(expired.connect(code), true)
  await setImmediate()
  assert.equal(expired.state.status, 'rejected')
  assert.equal(expired.state.caption, '')
})

test('all seven locales cover the UI and support system and manual selection', () => {
  const baseKeys = Object.keys(messages('en')).sort()
  for (const locale of Object.keys(languageNames)) {
    assert.equal(resolveLocale('system', [`${locale}-XX`]), locale)
    assert.equal(resolveLocale(locale, ['en-US']), locale)
    assert.deepEqual(Object.keys(messages(locale)).sort(), baseKeys)
    assert.ok(Object.values(messages(locale)).every(Boolean))
  }
  assert.equal(resolveLocale('system', ['pl-PL', 'da-DK']), 'da')
  assert.equal(resolveLocale('system', ['pl-PL']), 'en')
})
