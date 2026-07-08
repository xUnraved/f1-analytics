/**
 * Pinia-Store für Authentifizierungs-Zustand.
 *
 * Das Session-Token wird im localStorage unter TOKEN_KEY persistiert,
 * damit Seitenreloads die Anmeldung erhalten. initialized=true sobald
 * fetchMe() einmalig abgeschlossen ist (verhindert kurzes Login-Flackern).
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/** Eingeloggter Nutzer (Profil-Daten vom Backend). */
export interface AppUser {
  id: number
  username: string
  email: string
  /** Farbcode des Nutzers für Tippspiel-Anzeigen. */
  color: string
}

/** Antwort-Payload von /api/auth/register und /api/auth/login. */
interface AuthResult {
  token: string
  user: AppUser
}

/** localStorage-Key für das Session-Token (URL-safe Base64, 32 Bytes). */
const TOKEN_KEY = 'f1alytics.auth.token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const user = ref<AppUser | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  /** Wird nach dem ersten fetchMe()-Aufruf auf true gesetzt. */
  const initialized = ref(false)

  const isLoggedIn = computed(() => !!user.value && !!token.value)

  /** Setzt Token in State und localStorage; null = Token löschen. */
  function setToken(t: string | null) {
    token.value = t
    if (t) localStorage.setItem(TOKEN_KEY, t)
    else localStorage.removeItem(TOKEN_KEY)
  }

  /**
   * Prüft ein vorhandenes Token gegen GET /api/auth/me.
   * Bei 401/Fehler wird das Token entfernt.
   */
  async function fetchMe(): Promise<void> {
    if (!token.value) {
      user.value = null
      initialized.value = true
      return
    }
    try {
      const res = await fetch('/api/auth/me', {
        headers: { Authorization: 'Bearer ' + token.value },
      })
      if (res.ok) {
        user.value = await res.json()
      } else {
        setToken(null)
        user.value = null
      }
    } catch {
      user.value = null
    } finally {
      initialized.value = true
    }
  }

  /** Registriert einen neuen Account; gibt true zurück bei Erfolg. */
  async function register(username: string, email: string, password: string): Promise<boolean> {
    loading.value = true
    error.value = null
    try {
      const res = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, email, password }),
      })
      if (!res.ok) {
        error.value = await res.text().catch(() => 'Registrierung fehlgeschlagen')
        return false
      }
      const data: AuthResult = await res.json()
      setToken(data.token)
      user.value = data.user
      return true
    } catch {
      error.value = 'Netzwerkfehler bei der Registrierung'
      return false
    } finally {
      loading.value = false
    }
  }

  /**
   * Meldet einen Nutzer mit Username oder E-Mail + Passwort an.
   * Gibt true zurück bei Erfolg; setzt error.value bei 401.
   */
  async function login(loginValue: string, password: string): Promise<boolean> {
    loading.value = true
    error.value = null
    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ login: loginValue, password }),
      })
      if (!res.ok) {
        error.value = res.status === 401 ? 'Account oder Passwort falsch.' : await res.text()
        return false
      }
      const data: AuthResult = await res.json()
      setToken(data.token)
      user.value = data.user
      return true
    } catch {
      error.value = 'Netzwerkfehler beim Login'
      return false
    } finally {
      loading.value = false
    }
  }

  /**
   * Sendet DELETE-Token an das Backend und löscht lokalen Zustand.
   * Fehler beim Backend-Aufruf werden ignoriert (Token trotzdem lokal löschen).
   */
  async function logout(): Promise<void> {
    if (token.value) {
      try {
        await fetch('/api/auth/logout', {
          method: 'POST',
          headers: { Authorization: 'Bearer ' + token.value },
        })
      } catch {
        /* ignore */
      }
    }
    setToken(null)
    user.value = null
  }

  function clearError() {
    error.value = null
  }

  return {
    token,
    user,
    loading,
    error,
    initialized,
    isLoggedIn,
    fetchMe,
    register,
    login,
    logout,
    clearError,
  }
})

/**
 * Gibt den Authorization-Header mit dem gespeicherten Token zurück.
 * Kann außerhalb des Stores (z. B. in f1Api.ts) direkt genutzt werden.
 */
export function authHeaders(): Record<string, string> {
  const t = localStorage.getItem(TOKEN_KEY)
  return t ? { Authorization: 'Bearer ' + t } : {}
}
