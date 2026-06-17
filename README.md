# F1 Analytics

Lokale F1-Datenanalyse-App mit Quarkus-Backend und Vue-Frontend.

## Voraussetzungen

- Java 17+
- Maven 3.8+
- Node.js 18+

---

## Starten

**Zwei separate Terminals öffnen:**

### Terminal 1 — Backend
```powershell
cd d:\f1-analytics\backend
mvn quarkus:dev
```
Läuft auf: http://localhost:8081

### Terminal 2 — Frontend
```powershell
cd d:\f1-analytics\frontend
npm run dev
```
Läuft auf: http://localhost:5173

> Beim ersten Start lädt das Backend die Sessiondaten von der OpenF1-API. Danach werden sie lokal gecacht.

---

## Stoppen

In jedem Terminal: **Ctrl + C**

Oder alle Prozesse auf einmal (PowerShell):
```powershell
Stop-Process -Name "java" -Force
Stop-Process -Name "node" -Force
```

---

## URLs

| Dienst   | URL                                      |
|----------|------------------------------------------|
| Frontend | http://localhost:5173                    |
| Backend  | http://localhost:8081                    |
| API      | http://localhost:8081/api/sessions?year=2024 |

---

## Projektstruktur

```
f1-analytics/
├── backend/   → Quarkus REST-API (Java)
└── frontend/  → Vue 3 + Pinia (TypeScript)
```
