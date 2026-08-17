import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

const TOKEN_KEY = 'sky_user_token'
const PROFILE_KEY = 'sky_user_profile'

export interface UserProfile {
  id: number
  name: string
  phone: string
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const profile = ref<UserProfile | null>(JSON.parse(localStorage.getItem(PROFILE_KEY) || 'null'))
  const loggedIn = computed(() => Boolean(token.value))

  function setSession(nextToken: string, nextProfile: UserProfile) {
    token.value = nextToken
    profile.value = nextProfile
    localStorage.setItem(TOKEN_KEY, nextToken)
    localStorage.setItem(PROFILE_KEY, JSON.stringify(nextProfile))
  }

  function logout() {
    token.value = ''
    profile.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(PROFILE_KEY)
  }

  return { token, profile, loggedIn, setSession, logout }
})
