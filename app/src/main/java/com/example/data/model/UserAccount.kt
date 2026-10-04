package com.example.data.model

enum class UserRole {
    SUPER_ADMIN,
    ADMIN,
    GURU,
    WALI_SANTRI;

    companion object {
        fun fromString(roleStr: String?): UserRole {
            return when (roleStr?.trim()?.uppercase()) {
                "SUPER_ADMIN", "SUPERADMIN" -> SUPER_ADMIN
                "ADMIN" -> ADMIN
                "GURU", "GURU PIKET" -> GURU
                "WALI_SANTRI", "SANTRI", "SISWA" -> WALI_SANTRI
                else -> GURU
            }
        }
    }
}

data class UserAccount(
    val username: String,
    val password: String = "123",
    val displayName: String,
    val role: UserRole,
    val unitPendidikan: String? = null,
    val studentId: String? = null,
    val photoInitial: String = displayName.take(1).uppercase()
) {
    companion object {
        val DEFAULT_ACCOUNTS = listOf(
            UserAccount(
                username = "pernahngoding@gmail.com",
                password = "admin123",
                displayName = "Muhamad Yunus",
                role = UserRole.SUPER_ADMIN
            ),
            UserAccount(
                username = "admin",
                password = "admin",
                displayName = "Super Admin",
                role = UserRole.SUPER_ADMIN
            )
        )
    }
}