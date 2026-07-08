/**
 * vue-i18n Konfiguration.
 * Sprache wird aus localStorage ('f1alytics.locale') geladen; Fallback: 'de'.
 * Erlaubte Werte: 'de' | 'en'. Composition API-Modus (legacy: false).
 */
import { createI18n } from 'vue-i18n'
import de from './de'
import en from './en'

const saved = localStorage.getItem('f1alytics.locale')
const locale = saved === 'en' || saved === 'de' ? saved : 'de'

export const i18n = createI18n({
  legacy: false,
  locale,
  fallbackLocale: 'de',
  messages: { de, en },
})

export type Locale = 'de' | 'en'
