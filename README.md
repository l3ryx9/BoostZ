# BoostZ

Booster de volume et de basses + égaliseur global pour Android (Kotlin + Jetpack Compose), thème noir et vert néon.

## Ce que fait l'application

- **Moteur global** : tous les effets s'appliquent au mix système (session audio 0), donc à toutes les applications.
- **Principale** : deux gros boutons rotatifs.
  - Volume Booster : 0 à +30 dB (post-gain du limiteur).
  - Bass Booster : 0 à +15 dB sous 80 Hz (pré-égaliseur, comme l'original).
  - Carte « En lecture » (titre du lecteur en cours).
- **Equalizer** : 10 bandes, préréglages, Stéréo large, Clarté.
- Intro animée au lancement (le « Z » néon), logo casque + Z comme icône d'application.
- Menu ⋮ : démarrer / arrêter le moteur, démarrage avec le téléphone, relancer le moteur, choix de la langue.
- Langues : français, anglais, espagnol, russe, chinois, italien (par défaut : langue du téléphone, sinon anglais).

## Compiler l'APK (sans PC)

1. Poussez tout ce dossier sur un dépôt GitHub (branche `main`).
2. L'onglet **Actions** lance « Build APK » automatiquement (ou lancez-le à la main).
3. Téléchargez l'artefact **BoostZ-debug-apk** : il contient `app-debug.apk`.

Aucun wrapper Gradle n'est nécessaire : le workflow installe Gradle 8.9 lui-même.

## Structure

```
app/src/main/java/app/boostz/
  I18n.kt   (langues et traductions)
  audio/    Models.kt, Store.kt, EqEngine.kt, NowPlaying.kt
  service/  EqService.kt, BootReceiver.kt, MediaListener.kt
  ui/       Theme.kt, Widgets.kt, Knob.kt, Power.kt, MainScreen.kt, EqualizerScreen.kt, Root.kt
```
