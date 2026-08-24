package com.example.data.auth

data class User(
    val uid: String,
    val name: String,
    val email: String,
    val photoUrl: String? = null,
    val isPro: Boolean = true
)
