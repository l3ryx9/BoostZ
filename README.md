# BoostZ

Booster de volume et de basses + égaliseur global pour Android (Kotlin + Jetpack Compose), thème noir et vert néon.

## Ce que fait l'application

- **Moteur global** : tous les effets s'appliquent au mix système (session audio 0), donc à toutes les applications.
- **Principale** : deux gros boutons rotatifs.
  - Volume Booster : 0 à +15 dB de gain d'entrée, tenu par le limiteur (plus fort sans saturer).
  - Bass Booster : 0 à +15 dB sous 80 Hz (pré-égaliseur, comme l'original).
  - Auto EQ (Intelligent), carte « En lecture » (titre du lecteur en cours), Loudness 0 à +30 dB.
- **Equalizer** : 10 bandes, Auto EQ (IA), préréglages, Virtualiseur, Stéréo large, Clarté, état du moteur.
- Intro animée au lancement (le « Z » néon), logo casque + Z comme icône d'application.
- L'Auto EQ analyse le son global joué sur le téléphone (session audio 0), jamais le micro.
- Menu ⋮ : démarrer / arrêter le moteur, démarrage avec le téléphone, relancer le moteur.

## Compiler l'APK (sans PC)

1. Poussez tout ce dossier sur un dépôt GitHub (branche `main`).
2. L'onglet **Actions** lance « Build APK » automatiquement (ou lancez-le à la main).
3. Téléchargez l'artefact **BoostZ-debug-apk** : il contient `app-debug.apk`.

Aucun wrapper Gradle n'est nécessaire : le workflow installe Gradle 8.9 lui-même.

## Structure

```
app/src/main/java/app/boostz/
  audio/    Models.kt, Store.kt, EqEngine.kt, SmartEq.kt, NowPlaying.kt
  service/  EqService.kt, BootReceiver.kt, MediaListener.kt
  ui/       Theme.kt, Widgets.kt, Knob.kt, Power.kt, MainScreen.kt, EqualizerScreen.kt, Root.kt
```
