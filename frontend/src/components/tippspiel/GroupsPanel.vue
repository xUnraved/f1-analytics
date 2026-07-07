<template>
  <section class="card gp-card">
    <div class="gp-head">
      <div>
        <div class="gp-title">{{ t('groups.title') }}</div>
        <div class="gp-sub mono">{{ t('groups.subtitle') }}</div>
      </div>
    </div>

    <div class="gp-grid">
      <div class="gp-left">
        <div class="gp-block">
          <div class="gp-label mono">{{ t('groups.mine') }}</div>
          <div v-if="loadingGroups" class="gp-hint mono">{{ t('groups.loading') }}</div>
          <div v-else-if="!groups.length" class="gp-hint mono">{{ t('groups.none') }}</div>
          <div v-else class="gp-list">
            <button
              v-for="g in groups"
              :key="g.id"
              type="button"
              class="gp-item"
              :class="{ on: g.id === selectedId }"
              @click="selectGroup(g.id)"
            >
              <span class="gp-item-name">{{ g.name }}</span>
              <span class="gp-item-meta mono">{{ t('groups.membersShort', { n: g.memberCount }) }}<template v-if="g.isOwner"> · {{ t('groups.admin') }}</template></span>
            </button>
          </div>
        </div>

        <form class="gp-block gp-form" @submit.prevent="createGroup">
          <div class="gp-label mono">{{ t('groups.createLabel') }}</div>
          <div class="gp-inline">
            <input v-model.trim="newName" type="text" minlength="3" maxlength="48" :placeholder="t('groups.namePlaceholder')" required />
            <button type="submit" class="gbtn primary" :disabled="busy || newName.length < 3">{{ t('groups.create') }}</button>
          </div>
        </form>

        <form class="gp-block gp-form" @submit.prevent="joinGroup">
          <div class="gp-label mono">{{ t('groups.joinLabel') }}</div>
          <div class="gp-inline">
            <input v-model.trim="joinCode" type="text" maxlength="12" :placeholder="t('groups.codePlaceholder')" class="mono code-input" required />
            <button type="submit" class="gbtn" :disabled="busy || !joinCode">{{ t('groups.join') }}</button>
          </div>
        </form>

        <p v-if="message" class="gp-msg mono" :class="{ err: messageIsError }">{{ message }}</p>
      </div>

      <div class="gp-right">
        <template v-if="selected">
          <div class="gp-detail-head">
            <div>
              <div class="gp-detail-name">{{ selected.name }}</div>
              <div class="gp-detail-meta mono">{{ t('groups.adminLine', { name: selected.ownerName, n: selected.memberCount }) }}</div>
            </div>
            <button type="button" class="gbtn ghost small" :disabled="busy" @click="leaveGroup">{{ t('groups.leave') }}</button>
          </div>

          <div class="invite-box">
            <div>
              <div class="gp-label mono">{{ t('groups.inviteCode') }}</div>
              <div class="invite-code mono">{{ selected.inviteCode }}</div>
            </div>
            <button type="button" class="gbtn" @click="copyCode">{{ copied ? t('groups.copied') : t('groups.copy') }}</button>
          </div>

          <div class="gp-label mono members-label">{{ t('groups.members') }}</div>
          <div class="member-chips">
            <span v-for="m in members" :key="m.username" class="member-chip">
              <span class="mc-avatar" :style="{ background: m.color || '#888' }">{{ initial(m.username) }}</span>
              {{ m.username }}
              <span v-if="m.isOwner" class="mc-owner mono">{{ t('groups.adminTag') }}</span>
            </span>
          </div>

          <div class="gp-label mono members-label">{{ t('groups.scoreboard', { year: store.year }) }}</div>
          <div v-if="loadingLb" class="gp-hint mono">{{ t('groups.loadingLb') }}</div>
          <div v-else-if="!groupLb.length" class="gp-hint mono">{{ t('groups.noTips') }}</div>
          <div v-else class="glb-list">
            <div
              v-for="(e, i) in groupLb"
              :key="e.username"
              class="glb-row"
              :class="{ me: e.username === myName, top: i === 0 }"
            >
              <span class="glb-rank mono">{{ i + 1 }}</span>
              <span class="glb-avatar" :style="{ background: e.color || '#888' }">{{ initial(e.username) }}</span>
              <span class="glb-nick">
                {{ e.username }}
                <span v-if="e.username === myName" class="glb-me mono">{{ t('groups.you') }}</span>
              </span>
              <span class="glb-stats mono">{{ e.settledTipps }} {{ t('groups.settled') }} · {{ e.openTipps }} {{ t('groups.open') }}</span>
              <span class="glb-points mono">{{ e.totalPoints }}</span>
            </div>
          </div>
        </template>

        <div v-else class="gp-empty mono">
          {{ t('groups.empty') }}
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSeasonStore } from '@/stores/seasonStore'
import { useAuthStore, authHeaders } from '@/stores/authStore'

interface GroupDto {
  id: number
  name: string
  inviteCode: string
  ownerName: string
  memberCount: number
  isOwner: boolean
}
interface MemberDto {
  username: string
  color: string | null
  isOwner: boolean
  joinedAt: string
}
interface LeaderboardEntry {
  username: string
  color: string | null
  totalPoints: number
  settledTipps: number
  openTipps: number
}

const { t, locale } = useI18n()
const store = useSeasonStore()
const auth = useAuthStore()

const groups = ref<GroupDto[]>([])
const selectedId = ref<number | null>(null)
const members = ref<MemberDto[]>([])
const groupLb = ref<LeaderboardEntry[]>([])

const loadingGroups = ref(false)
const loadingLb = ref(false)
const busy = ref(false)
const copied = ref(false)

const newName = ref('')
const joinCode = ref('')
const message = ref('')
const messageIsError = ref(false)

const myName = computed(() => auth.user?.username ?? '')
const selected = computed(() => groups.value.find((g) => g.id === selectedId.value) ?? null)

function initial(s: string): string {
  return s ? s.trim().charAt(0).toUpperCase() : '?'
}

function setMessage(text: string, isError: boolean) {
  message.value = text
  messageIsError.value = isError
  setTimeout(() => {
    if (message.value === text) message.value = ''
  }, 4000)
}

async function loadGroups() {
  if (!auth.isLoggedIn) return
  loadingGroups.value = true
  try {
    const res = await fetch('/api/groups/mine', { headers: authHeaders() })
    groups.value = res.ok ? await res.json() : []
    if (groups.value.length && !groups.value.some((g) => g.id === selectedId.value)) {
      selectedId.value = groups.value[0]?.id ?? null
    }
    if (!groups.value.length) selectedId.value = null
  } catch {
    groups.value = []
  } finally {
    loadingGroups.value = false
  }
}

async function loadDetail() {
  if (selectedId.value == null) {
    members.value = []
    groupLb.value = []
    return
  }
  loadingLb.value = true
  try {
    const [mRes, lbRes] = await Promise.all([
      fetch(`/api/groups/${selectedId.value}/members`, { headers: authHeaders() }),
      fetch(`/api/groups/${selectedId.value}/leaderboard/${store.year}`, { headers: authHeaders() }),
    ])
    members.value = mRes.ok ? await mRes.json() : []
    groupLb.value = lbRes.ok ? await lbRes.json() : []
  } catch {
    members.value = []
    groupLb.value = []
  } finally {
    loadingLb.value = false
  }
}

function selectGroup(id: number) {
  selectedId.value = id
}

async function createGroup() {
  busy.value = true
  try {
    const res = await fetch('/api/groups', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify({ name: newName.value }),
    })
    if (!res.ok) {
      setMessage(await serverError(res, t('groups.errCreate')), true)
      return
    }
    const g: GroupDto = await res.json()
    newName.value = ''
    setMessage(t('groups.createdMsg', { name: g.name, code: g.inviteCode }), false)
    await loadGroups()
    selectedId.value = g.id
  } catch {
    setMessage(t('groups.errNetwork'), true)
  } finally {
    busy.value = false
  }
}

async function joinGroup() {
  busy.value = true
  try {
    const res = await fetch('/api/groups/join', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify({ code: joinCode.value }),
    })
    if (!res.ok) {
      setMessage(await serverError(res, t('groups.errJoin')), true)
      return
    }
    const g: GroupDto = await res.json()
    joinCode.value = ''
    setMessage(t('groups.joinedMsg', { name: g.name }), false)
    await loadGroups()
    selectedId.value = g.id
  } catch {
    setMessage(t('groups.errNetwork'), true)
  } finally {
    busy.value = false
  }
}

async function leaveGroup() {
  if (selectedId.value == null || !selected.value) return
  if (!confirm(t('groups.confirmLeave', { name: selected.value.name }))) return
  busy.value = true
  try {
    const res = await fetch(`/api/groups/${selectedId.value}/leave`, {
      method: 'POST',
      headers: authHeaders(),
    })
    if (!res.ok) {
      setMessage(await serverError(res, t('groups.errLeave')), true)
      return
    }
    selectedId.value = null
    await loadGroups()
  } catch {
    setMessage(t('groups.errNetwork'), true)
  } finally {
    busy.value = false
  }
}

async function copyCode() {
  if (!selected.value) return
  try {
    await navigator.clipboard.writeText(selected.value.inviteCode)
    copied.value = true
    setTimeout(() => (copied.value = false), 1600)
  } catch {
    setMessage(t('groups.copyFail', { code: selected.value.inviteCode }), true)
  }
}

async function serverError(res: Response, fallback: string): Promise<string> {
  if (locale.value !== 'de') return fallback
  const txt = await res.text().catch(() => '')
  return txt || fallback
}

onMounted(loadGroups)
watch(selectedId, loadDetail)
watch(() => store.year, loadDetail)
watch(() => auth.isLoggedIn, (loggedIn) => {
  if (loggedIn) loadGroups()
  else {
    groups.value = []
    selectedId.value = null
  }
})
</script>

<style scoped>
.gp-card {
  padding: 20px 22px;
}
.gp-head {
  margin-bottom: 16px;
}
.gp-title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  color: var(--text);
}
.gp-sub {
  font-size: 10px;
  letter-spacing: 0.1em;
  color: var(--text-faint);
  margin-top: 4px;
}
.gp-grid {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) 2fr;
  gap: 20px;
}
.gp-left {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.gp-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.gp-label {
  font-size: 9px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--text-faint);
}
.gp-hint {
  font-size: 10px;
  letter-spacing: 0.08em;
  color: var(--text-faint);
  opacity: 0.8;
}
.gp-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.gp-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 10px 12px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
  text-align: left;
}
.gp-item:hover {
  border-color: var(--accent);
}
.gp-item.on {
  border-color: var(--accent);
  background: color-mix(in srgb, var(--accent) 8%, var(--bg));
}
.gp-item-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.gp-item-meta {
  font-size: 9px;
  letter-spacing: 0.08em;
  color: var(--text-faint);
  white-space: nowrap;
}
.gp-inline {
  display: flex;
  gap: 8px;
}
.gp-inline input {
  flex: 1;
  min-width: 0;
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px 12px;
  color: var(--text);
  font-family: var(--font-body);
  font-size: 13px;
  outline: none;
  transition: border-color 0.15s;
}
.gp-inline input:focus {
  border-color: var(--accent);
}
.code-input {
  text-transform: uppercase;
  letter-spacing: 0.2em;
}
.gbtn {
  background: transparent;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px 16px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: var(--text-dim);
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, opacity 0.15s;
  white-space: nowrap;
}
.gbtn:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--text);
}
.gbtn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.gbtn.primary {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}
.gbtn.primary:hover:not(:disabled) {
  opacity: 0.85;
}
.gbtn.ghost.small {
  padding: 6px 12px;
  font-size: 9px;
}
.gp-msg {
  font-size: 10px;
  letter-spacing: 0.06em;
  color: #22c55e;
  margin: 0;
}
.gp-msg.err {
  color: #e10600;
}

.gp-right {
  border-left: 1px solid var(--line-soft);
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 200px;
}
.gp-detail-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.gp-detail-name {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 700;
  color: var(--text);
}
.gp-detail-meta {
  font-size: 9px;
  letter-spacing: 0.12em;
  color: var(--text-faint);
  margin-top: 2px;
}
.invite-box {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  background: var(--bg);
  border: 1px dashed var(--line);
  border-radius: 10px;
  padding: 12px 14px;
}
.invite-code {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.3em;
  color: var(--accent);
  margin-top: 2px;
}
.members-label {
  margin-top: 4px;
}
.member-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.member-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 5px 12px 5px 5px;
  font-size: 12px;
  color: var(--text-dim);
}
.mc-avatar {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
}
.mc-owner {
  font-size: 8px;
  letter-spacing: 0.16em;
  color: var(--accent);
}
.glb-list {
  display: flex;
  flex-direction: column;
}
.glb-row {
  display: grid;
  grid-template-columns: 28px 30px 1fr auto 56px;
  align-items: center;
  gap: 10px;
  padding: 8px 6px;
  border-bottom: 1px solid var(--line-soft);
}
.glb-row:last-child {
  border-bottom: none;
}
.glb-row.top {
  background: linear-gradient(90deg, rgba(250, 204, 21, 0.08), transparent 70%);
  border-radius: 8px;
}
.glb-row.me .glb-nick {
  color: var(--accent);
}
.glb-rank {
  font-size: 11px;
  color: var(--text-faint);
  text-align: center;
}
.glb-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
}
.glb-nick {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.glb-me {
  font-size: 8px;
  letter-spacing: 0.16em;
  color: var(--accent);
  border: 1px solid color-mix(in srgb, var(--accent) 50%, transparent);
  border-radius: 999px;
  padding: 2px 7px;
}
.glb-stats {
  font-size: 9px;
  letter-spacing: 0.06em;
  color: var(--text-faint);
  white-space: nowrap;
}
.glb-points {
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
  text-align: right;
}
.gp-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  font-size: 10px;
  letter-spacing: 0.1em;
  color: var(--text-faint);
  text-align: center;
  padding: 30px 20px;
}

@media (max-width: 860px) {
  .gp-grid {
    grid-template-columns: 1fr;
  }
  .gp-right {
    border-left: none;
    padding-left: 0;
    border-top: 1px solid var(--line-soft);
    padding-top: 16px;
  }
  .glb-stats {
    display: none;
  }
}
</style>
