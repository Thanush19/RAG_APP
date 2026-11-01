package com.ragclient.android.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthReducerTest {

    @Test
    fun `typing clears the previous error`() {
        val failed = AuthState(email = "a@b.com", error = "Wrong password.")

        val typed = AuthReducer.reduce(
            failed,
            AuthResult.FieldChanged(AuthField.Password, "hunter2abc")
        )

        assertNull(typed.error)
        assertEquals("hunter2abc", typed.password)
    }

    @Test
    fun `switching mode resets the password and error`() {
        val signIn = AuthState(
            mode = AuthMode.SignIn,
            password = "secret12",
            error = "Nope."
        )

        val register = AuthReducer.reduce(signIn, AuthResult.ModeChanged(AuthMode.Register))

        assertEquals(AuthMode.Register, register.mode)
        assertEquals("", register.password)
        assertNull(register.error)
        assertFalse(register.isSignedIn)
    }

    @Test
    fun `success signs in and forgets the password`() {
        val inFlight = AuthState(
            email = "demo@example.com",
            password = "hunter2abc",
            inFlight = true
        )

        val done = AuthReducer.reduce(inFlight, AuthResult.SubmitSucceeded)

        assertTrue(done.isSignedIn)
        assertFalse(done.inFlight)
        assertEquals("", done.password)
    }

    @Test
    fun `validation blocks weak passwords before any network call`() {
        val problem = AuthReducer.validate(
            AuthState(email = "demo@example.com", password = "short")
        )

        assertEquals("Password must be at least 8 characters.", problem)
    }

    @Test
    fun `register mode requires a name`() {
        val problem = AuthReducer.validate(
            AuthState(mode = AuthMode.Register, email = "demo@example.com", password = "hunter2abc")
        )

        assertEquals("Name is required.", problem)
        assertNull(
            AuthReducer.validate(
                AuthState(
                    mode = AuthMode.Register,
                    name = "Demo",
                    email = "demo@example.com",
                    password = "hunter2abc"
                )
            )
        )
    }

    @Test
    fun `sign out returns to a clean form`() {
        val signedIn = AuthState(
            email = "demo@example.com",
            password = "hunter2abc",
            isSignedIn = true
        )

        val signedOut = AuthReducer.reduce(signedIn, AuthResult.SignedOut)

        assertFalse(signedOut.isSignedIn)
        assertEquals("", signedOut.email)
        assertEquals(AuthMode.SignIn, signedOut.mode)
    }

    @Test
    fun `session initials fall back to the email`() {
        assertEquals(
            "DE",
            com.ragclient.android.data.storage.Session(
                isAuthenticated = true,
                email = "demo@example.com"
            ).initials
        )
        assertEquals(
            "DU",
            com.ragclient.android.data.storage.Session(
                isAuthenticated = true,
                displayName = "Demo User",
                email = "demo@example.com"
            ).initials
        )
    }
}
