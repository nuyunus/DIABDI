package com.example.data.model

enum class UserRole {
    SUPER_ADMIN,
    ADMIN,
    GURU,
    WALI_SANTRI;

    companion object {
        fun fromString(roleStr: String?): UserRole {
            val s = roleStr?.trim()?.uppercase() ?: return GURU
            return when {
                s.contains("SUPER") -> SUPER_ADMIN
                s.contains("ADMIN") -> ADMIN
                s.contains("GURU") -> GURU
                s.contains("WALI") || s.contains("SANTRI") || s.contains("SISWA") -> WALI_SANTRI
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