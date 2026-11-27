package com.example.hisab.features.notifications

data class PushPreferences(val debts: Boolean = true, val payments: Boolean = true, val invitations: Boolean = true,
    val recovery: Boolean = true, val updates: Boolean = true, val previews: Boolean = false) {
    fun accepts(kind: String) = when (kind) {
        "DEBT" -> debts; "PAYMENT" -> payments; "INVITATION" -> invitations; "RECOVERY" -> recovery; "UPDATE" -> updates
        else -> false
    }
}
data class PushSettings(val preferences: PushPreferences, val deliveryConfigured: Boolean)
data class PushDevice(val installationId: String, val installationSecret: String, val token: String,
    val packageName: String, val versionCode: Int, val permissionGranted: Boolean, val bindingRevision: Long)
data class RemovePushDevice(val installationId: String, val installationSecret: String)
data class RevokePushDevice(val installationId: String, val installationSecret: String, val bindingRevision: Long)
data class PushEvent(val eventId: String, val ownerId: String, val kind: String, val title: String, val body: String,
    val groupId: String, val recordId: String, val sourceId: String, val expiresAt: Long) {
    companion object {
        private val uuid = Regex("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$")
        fun parse(data: Map<String, String>, owner: String?, now: Long): PushEvent? {
            if (owner == null || data["ownerId"] != owner || !uuid.matches(data["eventId"].orEmpty())) return null
            val kind = data["kind"] ?: return null
            if (kind !in setOf("DEBT", "PAYMENT", "INVITATION", "RECOVERY", "UPDATE")) return null
            val group = data["groupId"].orEmpty()
            val record = data["recordId"].orEmpty()
            val source = data["sourceId"].orEmpty()
            if (kind in setOf("DEBT", "PAYMENT", "INVITATION") && !uuid.matches(group)) return null
            if (record.isNotEmpty() && !uuid.matches(record)) return null
            if (kind in setOf("RECOVERY", "INVITATION") && !uuid.matches(source)) return null
            if (kind == "UPDATE" && !Regex("^v?[0-9]+\\.[0-9]+\\.[0-9]+$").matches(source)) return null
            val expiry = data["expiresAt"]?.toLongOrNull() ?: return null
            if (expiry <= now || expiry > now + 8L * 86_400_000) return null
            return PushEvent(data.getValue("eventId"), owner, kind, data["title"].orEmpty().take(120), data["body"].orEmpty().take(1500), group, record, source, expiry)
        }
    }
}
