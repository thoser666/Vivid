# 📖 Vivid — Guide de l'utilisateur

> **Version courte :** saisir l'URL et la clé de stream → toucher **Go Live** → terminé.
> Ce guide détaille chaque étape.

Vivid est une application de streaming IRL pour Android (RTMP/SRT/RTMPS) qui réunit overlay de chat,
bot IA et contrôle de la caméra dans une seule app — inspirée de [Moblin](https://github.com/eerimoq/moblin).

- 📥 **Installer :** [page des versions](../../releases) ou [Obtainium](#1-installation)
- 🤖 **Doc du bot :** [docs/ai-chat-bot.md](ai-chat-bot.md)
- 📋 **Feuille de route :** [PARITY.md](../PARITY.md) · [RELEASE.md](../RELEASE.md)
- ❓ **Problèmes :** [FAQ dans le README](../README.md#-faq--häufige-probleme)
- 🇩🇪 **Version allemande :** [docs/user-guide.md](user-guide.md) · 🇬🇧 **English version :** [docs/user-guide.en.md](user-guide.en.md)

---

## Table des matières

1. [Installation](#1-installation)
2. [Premier démarrage & autorisations](#2-premier-démarrage--autorisations)
3. [Configurer le stream](#3-configurer-le-stream)
4. [Go Live](#4-go-live)
5. [Contrôle de la caméra (tap-to-focus, zoom, stabilisation, lampe torche)](#5-contrôle-de-la-caméra)
6. [Overlay de chat](#6-overlay-de-chat)
7. [Chat-bot IA](#7-chat-bot-ia)
8. [Contrôler OBS Studio](#8-contrôler-obs-studio)
9. [Télécommande web (piloter le stream depuis un navigateur)](#9-télécommande-web)
10. [Paramètres — les six catégories](#10-paramètres--les-six-catégories)
11. [Mises à jour](#11-mises-à-jour)

---

## 1. Installation

> Vivid n'est pas encore sur le Play Store. Les APK sont publiées comme releases GitHub.

1. Ouvre la [**page des versions**](../../releases)
2. Télécharge **« Latest »** (stable) ou une **Nightly/Alpha** (prerelease) — le fichier s'appelle `app-release.apk`
3. Ouvre l'APK dans le gestionnaire de fichiers → Android demande d'autoriser **« Sources inconnues »** → accepter
4. **Installer** — terminé

**Alternative avec Obtainium (mises à jour automatiques) :**
Voir [README → Automatic updates (F-Droid Repository)](../README.md#-automatic-updates-f-droid-repository).

> 🔒 Toutes les APK officielles sont signées avec la même clé de release — les mises à jour s'installent sans problème par-dessus.

---

## 2. Premier démarrage & autorisations

Au premier lancement, Vivid affiche l'**écran de streaming** (aperçu en direct). Avant de toucher **Go Live** pour la première fois, l'app a besoin de trois autorisations :

| Autorisation | Pourquoi | Quand demandée |
|---|---|---|
| **Caméra** | Diffuser l'image en direct de l'appareil | Au premier **Go Live** |
| **Micro** | Enregistrer l'audio/le commentaire | Au premier **Go Live** |
| **Notifications** | Service au premier plan (le stream continue en arrière-plan) | Au premier **Go Live** (depuis Android 13) |

> **Streaming en arrière-plan :** le stream continue si tu quittes l'app (touche Accueil),
> éteins l'écran ou balaies l'app hors de la liste — l'aperçu revient automatiquement
> à la réouverture. Une notification persistante avec action **Stop** affiche le statut du stream.

---

## 3. Configurer le stream

Ouvre les **paramètres** (⚙️ en haut à droite de l'écran de streaming) → **« Streaming & OBS »**.

### 3.1 Choisir un modèle de plateforme

Vivid propose des modèles pour les plateformes courantes :

| Modèle | URL du serveur | Ce qu'il te faut |
|---|---|---|
| **Twitch** | `rtmp://live.twitch.tv/live/` | Clé de stream (depuis le [Creator Dashboard](https://dashboard.twitch.tv/) → Settings → Stream) |
| **YouTube** | *[depuis YouTube Studio]* | URL + clé de stream (depuis [YouTube Studio](https://studio.youtube.com/) → Go Live → Stream) |
| **Kick** | `rtmp://ingest.kick.com/live/` | Clé de stream (depuis le [Kick Dashboard](https://kick.com/dashboard) → Settings → Stream Settings) |
| **Personnalisé** | *(vide — tu saisis l'URL toi-même)* | URL RTMP(S)/SRT + clé de stream (p. ex. [Owncast](https://owncast.online), Restream.io, etc.) |

### 3.2 Remplir les champs

1. Toucher le **modèle de plateforme** → l'URL est préremplie
2. Adapter si besoin l'**URL du stream** (p. ex. `rtmps://` pour TLS — le toggle **« TLS »** active le chiffrement)
3. Saisir la **clé de stream**
4. **Terminé** — les paramètres sont enregistrés

### 3.3 Multi-streaming (deux cibles en parallèle)

En option, tu peux configurer une **deuxième cible RTMP** :
- Dans **« Multi-streaming (optionnel) »**, saisir l'URL + la clé secondaires
- Au **Go Live**, les deux cibles démarrent en même temps
- Chaque cible affiche son propre statut (prêt / connexion… / en direct / échec)
- Si une cible tombe, seule celle-ci s'arrête — l'autre continue de diffuser

> Idéal pour diffuser en même temps vers Twitch + YouTube.

### 3.4 Auto-vérification avant le Go Live

Avant le démarrage, Vivid valide la configuration et affiche des messages clairs :
- ❌ **Erreur** (bloque le démarrage) : aucune URL, protocole non pris en charge, aucun hôte
- ⚠️ **Avertissement** (ne bloque pas) : clé de stream manquante (certaines plateformes n'en exigent pas)

---

## 4. Go Live

1. Retour à l'**écran de streaming** (← flèche en haut à gauche si tu es dans les paramètres)
2. Toucher le bouton **Go Live** (en bas au centre)
3. Vivid se connecte — le statut passe à **« Préparation… »** → **« en direct »**
4. L'affichage de statut montre chaque cible séparément (en multi-streaming)

### Arrêter le stream

- Bouton **Stop** sur l'écran de streaming, **ou**
- **Stop** dans la barre de notifications

> Le stream s'arrête proprement — le chat-bot IA (s'il est actif) s'arrête aussi automatiquement.

---

## 5. Contrôle de la caméra

Directement sur l'**aperçu du stream** (également utilisable **avant** le Go Live) :

| Action | Comment |
|---|---|
| **Tap-to-focus** | Toucher une fois l'aperçu → mise au point sur cet endroit |
| **Pincement-zoom** | Écarter/resserrer deux doigts → zoom (limité à la plage de la caméra) |
| **Réinitialiser le zoom** | Double-tap sur l'aperçu → zoom = 1.0 |

En haut à droite de l'écran de streaming, il y a trois boutons :

| Bouton | Fonction |
|---|---|
| **🔦 Lampe torche** | Allume/éteint la lampe torche — également contrôlable par le streamer via la commande bot `!torch` |
| **Stabilisation** | Stabilisation optique (OIS) préférée, sinon numérique (EIS) — on/off |
| **Verrou de mise au point** | Autofocus ⇄ verrou à l'infini (contre le pompage de mise au point par pluie/saleté sur l'objectif — idéal pour les streams en voiture/train) |

> Les boutons agissent sur la **vraie caméra RootEncoder** — pas seulement sur l'aperçu.

Dans la **barre supérieure** de l'écran de streaming se trouve aussi un bouton **❓ Aide**, qui ouvre l'aide intégrée à l'app : astuces rapides, aperçu des commandes du bot et liens directs vers ce guide (🇩🇪/🇬🇧/🇫🇷), la doc du bot et le suivi des problèmes.

---

## 6. Overlay de chat

L'overlay de chat Twitch affiche le chat de ta chaîne **au-dessus de l'aperçu du stream**.

### 6.1 Activer

1. **Paramètres** → **« Overlays & widgets »**
2. Activer **l'overlay de chat**
3. Saisir le **canal de chat** (ton nom de chaîne Twitch, p. ex. `thoser666`)

### 6.2 Ce qu'il te faut

L'overlay lit le chat via **Twitch EventSub** (pas IRC). Pour cela, il faut les
**identifiants du bot** (voir [Chat-bot IA](#7-chat-bot-ia) → login du bot + jeton OAuth) :
- Le jeton du bot doit contenir le scope `user:read:chat`
- Pour les alertes d'événements (follows/subs) : le bot doit être **modérateur** de la chaîne (`moderator:read:followers`)
  et posséder `channel:read:subscriptions`

### 6.3 Ce que l'overlay affiche

- Les derniers messages du chat en bas à gauche (avec la couleur Twitch de chaque utilisateur)
- **Badges Twitch** (broadcaster/mod/sub) devant le nom d'utilisateur en images CDN
- **Emotes intégrés** (emotes Twitch en images directement dans le texte, via Coil)
- **Alertes d'événements** en lignes colorées au-dessus des messages :
  - 🟢 Follow · 🟣 Sub · 🔵 Sub offert · 🟦 Resub · 🟠 Raid
  - Masquées automatiquement après 10 secondes

### 6.4 Alerte de test (avant le Go Live)

Pour tester l'overlay avant le stream :
- Saisir **`!testalert follow`** (ou `sub`, `gift`, `resub`, `raid`) dans le chat (owner uniquement)
- L'alerte apparaît immédiatement dans l'overlay

---

## 7. Chat-bot IA

Le bot se connecte automatiquement au chat Twitch au **Go Live** et s'arrête à la
**fin du stream**. Le guide complet se trouve dans [docs/ai-chat-bot.md](ai-chat-bot.md).

### 7.1 Choisir le mode

| Mode | Description |
|---|---|
| **Bot (comme Moblin)** | Commandes déterministes `!` (`!help`, `!uptime`, `!tts`, `!bot`) — **aucun LLM nécessaire** |
| **IA autonome** | L'IA décide elle-même si et comment répondre (y compris se taire délibérément) |

### 7.2 Configuration

**Paramètres** → **« Chat-bot & IA »** :

1. Saisir le **login du bot** (nom d'utilisateur Twitch du bot)
2. **Jeton OAuth Twitch** (scope `user:read:chat` + `user:write:chat` ; pour la modération `moderator:manage:banned_users`) — champ mot de passe avec toggle de visibilité
3. **ID client de l'app Twitch** (pour EventSub + Helix)
4. Pour le mode IA : saisir l'**URL de base LLM**, la **clé API** et le **modèle** (compatible OpenAI → OpenAI, Gemini, Groq, DeepSeek, Ollama en LAN)

### 7.3 Limites (protection des coûts)

Trois limites réglables (toutes `0` = désactivées) :
- **Cooldown par viewer** (défaut 60 s) — un viewer ne reçoit pas plus d'une réponse toutes les X secondes
- **Plafond par viewer par stream** — nombre max. de réponses par viewer et par stream
- **Budget horaire** — nombre max. de réponses par heure (plafond de coûts)

**Presets de démarrage rapide :** Locker (30/0/0) · Balanced (60/10/120) · Streng (180/5/60) — une touche remplit les trois champs. Le niveau choisi est mémorisé et restauré au démarrage de l'app.

**Consommation en direct** dans l'écran des paramètres : réponses/heure (vs. budget), total du stream, top viewer.

### 7.4 Commandes owner (streamer uniquement)

| Commande | Effet |
|---|---|
| `!start` / `!go-live` | Démarrer le stream |
| `!stop` / `!end` | Arrêter le stream |
| `!diag` / `!status` | Diagnostic : statut du stream, OBS, 11 vérifications de configuration + recommandation IA |
| `!ask <question>` | Question à l'IA owner exclusive (repli : IA des viewers, sinon déterministe) |
| `!testalert <type>` | Alerte de test pour l'overlay (`follow`/`sub`/`gift`/`resub`/`raid`) |
| `!torch` | Basculer la lampe torche (alias : `!lantern`/`!flashlight`) |
| `!ban <user>` | Bannir un viewer |
| `!timeout <user> <min?>` | Timeout d'un viewer (défaut 5 min) |
| `!delete <count?>` | Supprimer les derniers messages |

> Owner = badge Broadcaster **ou** liste d'autorisation (`chat_bot_owner_logins` dans les paramètres).
> Les réponses partent en **whisper** (privées) si le toggle est actif.
> Commandes viewers : `!help`, `!uptime`, `!tts`, `!song`, `!next`, `!pause`, `!bot`.

### 7.5 Coexistence avec d'autres bots

Si un autre bot tourne sur la même chaîne (p. ex. Rivulet) :
- **`chat_bot_ignore_bots`** : ignorer complètement les autres logins de bots
- **Portée des commandes** : `ALL` (toute commande), `MENTION` (uniquement `@vividbot !help`), `PREFIX` (uniquement `!v!help` avec préfixe `v`)
- Les commandes étrangères hors portée → pas d'écho « commande inconnue » (l'autre bot reste tranquille)

---

## 8. Contrôler OBS Studio

Vivid peut piloter OBS Studio via **WebSocket** (changer de scène, démarrer/arrêter l'enregistrement/le stream).

### Configuration

1. **Dans OBS :** Outils → **Paramètres du serveur WebSocket** → cocher *« Activer le serveur WebSocket »* (port `4455`)
2. Optionnel : définir un **mot de passe**
3. Déterminer l'**IP de l'ordinateur OBS** (Windows : `ipconfig`, Mac/Linux : `ip addr`) — téléphone et PC sur le **même Wi-Fi**
4. **Dans Vivid :** Paramètres → **Streaming & OBS** → section OBS :
   - Hôte = IP du PC
   - Port = `4455`
   - Mot de passe (si défini)
   - **Toggle TLS** (`wss://` pour l'accès à distance, `ws://` en LAN)
5. Ouvrir le **contrôle OBS** (icône en haut à gauche de l'écran de streaming)

> Des problèmes ? Voir la [FAQ OBS](../README.md#-faq--häufige-probleme).

### Importer OBS via QR-code

Si ton OBS affiche un QR-code (format `obsws://host:port/pw`), tu peux l'importer dans
l'écran des paramètres — hôte, port et mot de passe sont repris automatiquement.

---

## 9. Télécommande web

Pilote le stream depuis n'importe quel navigateur du même Wi-Fi :

1. **Paramètres** → **Remote & confidentialité** → **Télécommande web**
2. Noter le **jeton** (généré une seule fois)
3. Déterminer l'**IP du téléphone** (Android : Paramètres → Wi-Fi → réseau connecté → Détails)
4. **Interroger le statut** (sans jeton) :
   ```
   curl http://<ip-du-téléphone>:8080/status
   ```
5. **Démarrer/arrêter le stream** (avec jeton) :
   ```
   curl -X POST http://<ip-du-téléphone>:8080/start -H "Authorization: Bearer <jeton>"
   curl -X POST http://<ip-du-téléphone>:8080/stop  -H "Authorization: Bearer <jeton>"
   ```

> 🔒 Le serveur ne tourne que tant que l'app est ouverte. Les actions nécessitent le jeton.
> Android 17 : si `/status` est inaccessible → « Autoriser l'accès LAN pour la télécommande » dans les paramètres.

---

## 10. Paramètres — Les six catégories

L'écran des paramètres est organisé en six catégories (comme Moblin) :

| Catégorie | Contenu |
|---|---|
| 🎬 **Streaming & OBS** | URL/clé de stream, modèles de plateforme (Twitch/YouTube/Kick/Custom), multi-streaming, connexion OBS (hôte/port/mot de passe/TLS/import QR) |
| 🎨 **Apparence** | Mode de design (Système/Clair/Sombre/AMOLED) + couleur d'accent (6 couleurs choisies, vert Vivid par défaut) |
| 🧩 **Overlays & widgets** | Overlay de chat Twitch (chaîne + toggle), widget texte/info (heure/GPS/vitesse/altitude — chacun avec toggle + autorisation d'exécution) |
| 💬 **Chat-bot & IA** | Mode de fonctionnement, compte du bot (login/jeton/ID client), endpoint/clé/modèle LLM, prompt, cooldown, mentions only, limite de débit, limites + presets, accès owner (liste d'autorisation + IA owner), commandes média, accès aux notifications |
| 🔒 **Remote & confidentialité** | Télécommande web (jeton + accès LAN), rapports d'erreurs Sentry (toggle d'exclusion) |
| ℹ️ **À propos & mises à jour** | Version, badge de mise à jour, recherche manuelle de mises à jour (releases GitHub), notes de version |

---

## 11. Mises à jour

### Automatique (Obtainium)

Voir [README → Automatic updates (F-Droid Repository)](../README.md#-automatic-updates-f-droid-repository).

### Manuelle

1. **Paramètres** → **À propos & mises à jour**
2. Le **badge de mise à jour** apparaît automatiquement (cache 1 h) si une version plus récente existe
3. Toucher **« Rechercher des mises à jour »** → affiche la dernière release GitHub, notes de version incluses
4. Télécharger l'APK depuis la [page des versions](../../releases) et l'installer

> La vérification ne propose jamais de rétrogradation (Nightly → Nightly/Alpha/Beta/Stable).

---

## Référence rapide : toutes les commandes du bot

| Commande | Qui ? | Effet |
|---|---|---|
| `!help` / `!commands` / `!hilfe` | Tous | Afficher les commandes disponibles |
| `!uptime` | Tous | Afficher la durée du stream |
| `!tts` | Tous | Lecture du chat (synthèse vocale) on/off |
| `!bot` | Tous | Afficher les infos du bot |
| `!song` / `!nowplaying` | Tous | Titre actuel (lecteur multimédia) |
| `!next` / `!skip` | Owner + Mod | Titre suivant |
| `!pause` | Owner + Mod | Mettre en pause la lecture |
| `!play` | Owner + Mod | Reprendre la lecture |
| `!prev` / `!previous` | Owner + Mod | Titre précédent |
| `!tts` | Owner | Basculer la lecture à haute voix du chat |
| `!start` / `!go-live` | Owner | Démarrer le stream |
| `!stop` / `!end` | Owner | Arrêter le stream |
| `!diag` / `!status` | Owner | Lancement d'un diagnostic |
| `!ask <question>` | Owner | Question à l'IA owner |
| `!fix` | Owner | Réparer les problèmes automatiquement |
| `!testalert <type>` | Owner | Alerte de test pour l'overlay |
| `!torch` | Owner | Basculer la lampe torche |
| `!ban <user>` | Owner | Bannir un viewer |
| `!timeout <user> <min?>` | Owner | Timeout d'un viewer |
| `!delete <count?>` | Owner | Supprimer des messages |

> **Portée PREFIX** (coexistence) : `!v!help`, `!v!uptime`, … (préfixe `v` = par défaut).
> Les commandes sont insensibles à la casse et peuvent être au milieu du message (`@vividbot !help`).
