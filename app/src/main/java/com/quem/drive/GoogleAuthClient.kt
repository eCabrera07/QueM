package com.quem.drive

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

class GoogleAuthClient(private val context: Context) {
    val driveFileScope: Scope = Scope(DRIVE_FILE_SCOPE)

    fun signInOptions(): GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(driveFileScope)
        .build()

    fun lastSignedInAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    companion object {
        const val DRIVE_FILE_SCOPE: String = "https://www.googleapis.com/auth/drive.file"
    }
}
