package com.vivid.domain.model

sealed class RegistrationResult {
    data object Success : RegistrationResult()

    /**
     * Registrierung fehlgeschlagen.
     *
     * @param message Rohdetail der Ursache (Exception-Text, bleibt unübersetzt,
     *   i18n-plan §4) — leer, wenn die Ursache kein Detail liefert.
     * @param messageRes Ressourcen-ID (`@StringRes`) für den lokalisierten
     *   Rahmen, den die App selbst formuliert (z. B.
     *   `R.string.api_error_unknown`), wenn kein Rohdetail vorliegt. Vom
     *   Konsumenten erst bei der Anzeige aufzulösen — Muster wie
     *   `StreamingState.Failed.reasonRes` (App-eigene Ursache = Ressource).
     */
    data class Error(val message: String, val messageRes: Int? = null) : RegistrationResult()
}
