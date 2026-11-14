<img width="1080" height="2097" alt="104241" src="https://github.com/user-attachments/assets/d1fa0d93-2c3b-47a1-a1bc-79d5bbb1c362" />
<img width="1080" height="2038" alt="104239" src="https://github.com/user-attachments/assets/ffd5a540-fe32-45a3-a1e8-6e5efd6989c0" />
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
- Menu ⋮ : démarrer / arrêter le moteur, démarrage avec le téléphone, relancer le moteur.
- Langues : anglais (par défaut), français, espagnol, russe, chinois, italien, au choix via le drapeau en haut de la page principale.

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
