package com.hisab.server.features.accounts

import at.favre.lib.crypto.bcrypt.BCrypt

object PasswordUtil {
    fun acceptsInput(password: String): Boolean =
        password.isNotBlank() && password.toByteArray(Charsets.UTF_8).size <= 72

    fun meetsRequirements(password: String): Boolean = password.length >= 8 && acceptsInput(password) &&
        password.any(Char::isLetter) && password.any(Char::isDigit) && password.any { !it.isLetterOrDigit() }

    fun hash(password: String): String {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray())
    }

    fun verify(password: String, hash: String): Boolean {
        return BCrypt.verifyer().verify(password.toCharArray(), hash).verified
    }
}
