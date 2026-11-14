package com.hisab.server.features.accounts

import com.hisab.server.models.UserDocument
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfilePrivacyTest {
    @Test
    fun anotherMemberGetsPublicIdentityWithoutPrivateProfileFields() {
        val owner = UserDocument(
            id = "owner", name = "Owner", email = "owner@example.invalid", passwordHash = "unused",
            uniqueId = "member-42", profilePicUrl = "", themePreference = "dark", role = "admin"
        )

        val public = owner.toResponse("other-member")
        assertEquals("member-42", public.uniqueId)
        assertEquals("", public.email)
        assertEquals("system", public.themePreference)
        assertEquals("user", public.role)

        val private = owner.toResponse(owner.id)
        assertEquals(owner.email, private.email)
        assertEquals("dark", private.themePreference)
        assertEquals("admin", private.role)
    }
}
