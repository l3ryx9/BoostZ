package app.boostz.service

import android.service.notification.NotificationListenerService

/**
 * Service vide : Android exige un « écouteur de notifications » autorisé par l'utilisateur
 * pour qu'une application puisse lire le titre en cours dans les autres lecteurs.
 * Aucune notification n'est lue ni stockée.
 */
class MediaListener : NotificationListenerService()
