import translations from './locales.json' with { type: 'json' }

export type Locale = keyof typeof translations
export type Message = keyof typeof translations.en
export type LanguagePreference = Locale | 'system'

export const languageNames: Record<Locale, string> = {
  en: 'English', ja: '日本語', da: 'Dansk', de: 'Deutsch',
  fr: 'Français', es: 'Español', zh: '简体中文',
}

function supported(value: string): value is Locale {
  return Object.hasOwn(translations, value)
}

export function resolveLocale(preference: string, system: readonly string[]): Locale {
  if (supported(preference)) return preference
  for (const language of system) {
    const base = language.toLowerCase().split('-')[0]
    if (supported(base)) return base
  }
  return 'en'
}

export function messages(locale: Locale): Record<Message, string> {
  return translations[locale]
}

const preferenceKey = 'e2player.language'

export function readLanguagePreference(): LanguagePreference {
  try {
    const stored = localStorage.getItem(preferenceKey)
    if (stored && supported(stored)) return stored
  } catch {
    // Some embedded hosts restrict localStorage.
  }
  return 'system'
}

export function saveLanguagePreference(preference: LanguagePreference): void {
  try {
    localStorage.setItem(preferenceKey, preference)
  } catch {
    // The choice still applies for the current page.
  }
}
