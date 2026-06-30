import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface AppUser {
  id: number
  username: string
  email: string
  color: string
}

interface AuthResult {
  token: string
  user: AppUser
}

const TOKEN_KEY = 'f1alytics.auth.token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const user = ref<AppUser | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const initialized = ref(false)

  const isLoggedIn = computed(() => !!user.value && !!token.value)

  function setToken(t: string | null) {
    token.value = t
    if (t) localStorage.setItem(TOKEN_KEY, t)
    else localStorage.removeItem(TOKEN_KEY)
  }

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

export function authHeaders(): Record<string, string> {
  const t = localStorage.getItem(TOKEN_KEY)
  return t ? { Authorization: 'Bearer ' + t } : {}
}
