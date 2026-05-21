# Keeper

App Android nativa (Kotlin + Jetpack Compose) per gestire note in stile Google Keep, con sincronizzazione vera su Google Drive.

Package: `com.secondream.keeper`

---

## Setup completo passo passo (Windows + Git Bash)

Tutto quello che segue lo fai una volta sola. Tempo stimato: 30-40 minuti.

### Step 1 — Installa Java (se non ce l'hai)

In Git Bash:

```bash
keytool -help
```

Se ti dice "command not found", apri PowerShell come amministratore:

```powershell
winget install Microsoft.OpenJDK.17
```

Chiudi e riapri Git Bash, riprova `keetyool -help`. Deve funzionare.

### Step 2 — Scompatta la zip

Salva `keeper.zip` in una cartella, apri Git Bash:

```bash
cd ~/Documents
unzip keeper.zip
cd keeper
```

### Step 3 — Genera la keystore

Sempre dentro la cartella `keeper`:

```bash
keytool -genkey -v -keystore keeper-upload-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

Ti chiede:

* Password keystore: scegline una lunga (es. `KeeperKey2026!Strong`). Salvala subito da qualche parte.
* Re-enter: stessa password.
* First and last name: `Eugenio Casale`
* Organizational unit: `Cheip Studio`
* Organization: `Second Dream`
* City: `Sesto Calende`
* State: `VA`
* Country code (2 letters): `IT`
* Is the information correct: `yes`
* Enter key password for upload: premi Enter per usare la stessa password della keystore.

Risultato: file `keeper-upload-key.jks` nella cartella.

**CRITICAL**: salva il `.jks` e la password in un password manager. Se perdi questi file non potrai più aggiornare l'app sul Play Store mai più. La firma deve restare la stessa per sempre.

### Step 4 — Estrai la SHA-1

```bash
keytool -list -v -keystore keeper-upload-key.jks -alias upload
```

Inserisci la password. Nell'output trova la riga:

```
SHA1: 5A:8B:21:3F:9C:0D:E4:73:11:8A:6E:F2:42:88:91:CC:DE:42:81:97
```

Copia quei 40 caratteri (con i due punti) da qualche parte. Ti servono al prossimo step.

### Step 5 — Setup Google Cloud Console

Vai su https://console.cloud.google.com/ (accedi con l'account Google che userai per testare l'app).

**5.1 — Crea il progetto:**

* Click sul menu progetti in alto vicino al logo Google Cloud
* Click "Nuovo progetto"
* Nome: `Keeper App`
* Click "Crea", aspetta che lo crea, selezionalo

**5.2 — Abilita Drive API:**

* Menu ☰ → "API e servizi" → "Libreria"
* Cerca "Google Drive API"
* Click sul risultato → click "Abilita"

**5.3 — Configura schermata consenso OAuth:**

* Menu ☰ → "API e servizi" → "Schermata consenso OAuth"
* User Type: "Esterno" → Crea
* Compila:
  * Nome app: `Keeper`
  * Email assistenza utenti: la tua email
  * Logo, domini app: lascia vuoto
  * Email contatto sviluppatore: la tua email
* Salva e continua
* Ambiti: salta, "Salva e continua" (lasciamo vuoto)
* Utenti di test: click "ADD USERS" → aggiungi la tua email Google → Salva e continua
* Riepilogo: "Torna alla dashboard"

**5.4 — Crea OAuth Client ID Android:**

* Menu ☰ → "API e servizi" → "Credenziali"
* Click "+ CREA CREDENZIALI" → "ID client OAuth"
* Tipo di applicazione: **Android**
* Nome: `Keeper Android Client`
* Nome pacchetto: `com.secondream.keeper`
* Impronta digitale certificato SHA-1: incolla la SHA-1 di Step 4 (mantieni i due punti)
* Crea

Il Client ID che ti dà NON serve copiarlo nel codice. Android usa la firma del pacchetto per autenticare. Il Client ID identifica solo la richiesta su lato Google.

### Step 6 — Crea il repo GitHub e fai push

Vai su https://github.com/new:
* Nome repo: `keeper`
* Visibilità: a scelta
* **NON** aggiungere README, .gitignore o license
* Crea

Torna in Git Bash dentro la cartella `keeper`:

```bash
git init
git add -A
git commit -m "v0.1.0 — initial commit with real Drive sync"
git branch -M main
git remote add origin https://github.com/TUO_USER/keeper.git
git push -u origin main
```

Sostituisci `TUO_USER` col tuo username. Se ti chiede credenziali, GitHub vuole un Personal Access Token (non la password). Generalo qui: https://github.com/settings/tokens/new con scope `repo`. Incollalo come password.

### Step 7 — Aggiungi i secrets GitHub

**7.1 — Converti la keystore in base64:**

```bash
base64 -w 0 keeper-upload-key.jks > keystore.base64.txt
```

**7.2 — Apri `keystore.base64.txt` con Notepad**, è una lunga stringa su una riga sola. Selezionala tutta (Ctrl+A) e copia (Ctrl+C).

**7.3 — Sul repo GitHub** → `Settings` → `Secrets and variables` → `Actions` → "New repository secret". Aggiungi quattro secrets:

| Name | Valore |
|------|--------|
| `KEYSTORE_BASE64` | la lunga stringa da `keystore.base64.txt` |
| `STORE_PASSWORD` | la password della keystore di Step 3 |
| `KEY_ALIAS` | `upload` |
| `KEY_PASSWORD` | la stessa password della keystore (visto che hai premuto Enter al prompt) |

**Dopo aver aggiunto i secrets:**

```bash
rm keystore.base64.txt
```

### Step 8 — Prima release

```bash
git tag v0.1.0
git push --tags
```

Vai sul repo GitHub → tab `Actions` → vedrai partire "Release APK and AAB". Dopo 5-10 minuti finisce.

Vai sul tab `Releases` (a destra della home del repo) → trovi il tag `v0.1.0` con `keeper-v0.1.0.apk` e `keeper-v0.1.0.aab` allegati. Scarica l'APK sul telefono Android, installa.

### Step 9 — Connetti l'app a Drive

Apri Keeper sul telefono:

1. Tab Impostazioni
2. Sezione "Cloud Backup" → "Connetti Google"
3. Appare il selettore Android account → scegli l'account che hai messo come "utente test" su Step 5.3
4. Prima volta Android chiede consenso "Keeper vuole accedere ai file Drive che ha creato" → Accetta
5. Attiva la levetta "Caricamento automatico su Drive"
6. Crea una nota con titolo, testo, e magari una foto allegata
7. Apri https://drive.google.com sul browser → vedi una cartella `Keeper` con dentro `note_1_titolonota/` con `note.json` + la foto

## Workflow di rilascio (uso quotidiano dopo il setup)

Dopo la setup iniziale, ogni nuova versione è:

```bash
# Aggiorna versionCode e versionName in app/build.gradle.kts
git add -A
git commit -m "v0.2.0 — descrizione cambi"
git push
git tag v0.2.0
git push --tags
```

GitHub Actions builda APK + AAB firmati e crea la release.

**IMPORTANTE**: `versionCode` deve essere INCREMENTATO ad ogni release (1, 2, 3...), altrimenti Play Store rifiuta. `versionName` è la stringa che vedi nell'app (`1.0`, `1.0.1`, eccetera).

## Build debug rapida

Ogni push su `main` triggera automaticamente un APK debug, scaricabile da `Actions → Debug APK on push → ultimo run → Artifacts`. Comodo per testare senza taggare.

## Build locale in Android Studio (opzionale)

Solo se vuoi lavorare offline:

1. Android Studio (Iguana o più recente)
2. JDK 17
3. Apri la cartella `keeper`
4. Lascia che sistemi le dipendenze
5. Run su emulatore o device per debug APK (firma automatica)

Per release firmato locale (richiede `.env` file vuoto se la build si lamenta):

```bash
export KEYSTORE_PATH=$(pwd)/keeper-upload-key.jks
export STORE_PASSWORD=la_tua_password
export KEY_ALIAS=upload
export KEY_PASSWORD=la_tua_password
./gradlew assembleRelease bundleRelease
```

Output in `app/build/outputs/`.

---

## Come funziona la sync Drive (quello che ti interessa sapere)

### Cosa fa l'auto-upload

Quando attivi "Caricamento automatico su Drive" nelle impostazioni:

* Ogni volta che crei, modifichi, pinni, archivi, cestini o ripristini una nota, l'app fa upload in background
* Best-effort: se Drive non risponde (offline, errore di rete), l'app non blocca la modifica locale, riproverà la prossima volta
* L'utente non vede notifiche: è silent

### Struttura su Drive

Sulla tua Drive personale, sotto la root (la "Il mio Drive"), trovi:

```
Keeper/
├── note_1_lista_spesa/
│   ├── note.json          (titolo, testo, checklist, colore, labels, timestamp)
│   ├── img_a3b8c9d2_foto.jpg
│   └── img_8e1f7c20_scontrino.jpg
├── note_2_idea_progetto/
│   ├── note.json
│   ├── vid_2c1d9a4f_demo.mp4
│   └── doc_91b3c5e7_specs.pdf
└── note_5_promemoria_meeting/
    └── note.json
```

Ogni nota = una cartella. Dentro: `note.json` (sempre) + tutti gli allegati con prefisso `img_`, `vid_`, `aud_`, `doc_` seguito dall'ID corto dell'allegato e il nome originale.

### Cosa succede quando elimini una nota

* **Sposti nel cestino dell'app**: la cartella Drive resta intatta (sync update del `note.json` con `isTrashed: true`)
* **Eliminazione permanente** (svuota cestino o elimina dal cestino): la cartella Drive viene eliminata, foto e video inclusi
* **Ripristina dal cestino**: il `note.json` su Drive viene aggiornato con `isTrashed: false`

### Scope OAuth: drive.file

L'app usa lo scope `drive.file`, che è "non sensibile". Significa:

* L'app vede solo i file che ha creato lei stessa
* NON vede gli altri tuoi file Drive
* Non richiede verifica Google (quello che richiederebbe screencast + privacy policy + 6 settimane di review)

Conseguenza pratica: se cancelli la app o cambi telefono, l'app NON può "scoprire" i suoi vecchi file su Drive senza ID memorizzati nel DB locale. È una caratteristica voluta dello scope `drive.file`. Per ora questo è OK perché:

* L'app salva in locale il Drive folder ID di ogni nota
* La connessione è persistente (resta finché l'utente non disconnette)
* Per il restore-da-altro-device serviranno passi successivi (TODO prossime sessioni)

---

## Cambiamenti tecnici rispetto allo zip AI Studio originale

**Rename pacchetto**: `com.example` → `com.secondream.keeper` ovunque.

**Drive sync REALE** (sostituisce kvdb.io fake):
* Nuovo package `data/drive/` con `DriveSync.kt` (REST API client OkHttp) e `DriveSyncRepository.kt` (logica per-nota)
* Auth via `GoogleAuthUtil.getToken` con scope `drive.file`
* Resumable upload per file grandi
* Cartella per nota + tutti gli allegati binari
* Eliminazione automatica da Drive quando nota eliminata permanentemente

**Schema DB**: aggiunto `driveFolderId: String?` e `driveSyncedAt: Long` a `Note`. DB version bumped a 2 (destructive migration: cancella le note esistenti al primo avvio della nuova versione).

**Player video** riscritto con Media3 ExoPlayer:
* `useController = false` (zero overlay nativi)
* Singolo set di controlli custom (slider + restart + play/pause)
* Rimosso il doppio bottone PAUSA/AVVIA

**Dettatura vocale reale** con `SpeechRecognizer`:
* Trascrizione live tramite `onPartialResults`
* Permesso `RECORD_AUDIO` gestito
* Errori esposti in italiano

**CI completa**: GitHub Actions identico a Speed Launcher.

---

## Cosa rimane per le prossime sessioni

* Bidirectional sync (download da Drive su altro device dello stesso account)
* Reminder (schermata già esistente come enum, vuota)
* Camera capture diretto (oltre al picker)
* Registrazione audio reale (oltre alla dettatura)
* Design pixel-style pass completo
* Pubblicazione Play Store
