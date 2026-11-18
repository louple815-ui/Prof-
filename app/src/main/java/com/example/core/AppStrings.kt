package com.example.core

import androidx.compose.runtime.compositionLocalOf

data class AppCopy(
    val lang: String,
    val slogan: String,
    val navHome: String,
    val navDebates: String,
    val navGroups: String,
    val navResources: String,
    val navProfile: String,
    val messages: String,
    val notifications: String,
    val establishments: String,
    val aiAssistant: String,
    val settings: String,
    val adminDashboard: String,
    val offlineBanner: String,
    val signInTitle: String,
    val signInSubtitle: String,
    val signInGoogleBtn: String,
    val signOutBtn: String,
    val publishBtn: String,
    val cancelBtn: String,
    val saveBtn: String,
    val searchPlaceholder: String,
    val noDataYet: String
)

fun copyFor(lang: String): AppCopy = if (lang == "en") {
    AppCopy(
        lang = "en",
        slogan = "The professional network for educators",
        navHome = "Home",
        navDebates = "Debates",
        navGroups = "Groups",
        navResources = "Resources",
        navProfile = "Profile",
        messages = "Messages",
        notifications = "Notifications",
        establishments = "Schools",
        aiAssistant = "AI Assistant",
        settings = "Settings",
        adminDashboard = "Admin",
        offlineBanner = "Offline mode — Downloaded resources remain available",
        signInTitle = "Welcome to prof+",
        signInSubtitle = "Connect with teachers and school leaders across Burkina Faso and Africa.",
        signInGoogleBtn = "Continue with Google",
        signOutBtn = "Sign out",
        publishBtn = "Publish",
        cancelBtn = "Cancel",
        saveBtn = "Save",
        searchPlaceholder = "Search...",
        noDataYet = "No items published yet."
    )
} else {
    AppCopy(
        lang = "fr",
        slogan = "Le réseau professionnel des enseignants",
        navHome = "Accueil",
        navDebates = "Débats",
        navGroups = "Groupes",
        navResources = "Ressources",
        navProfile = "Profil",
        messages = "Messages",
        notifications = "Notifications",
        establishments = "Établissements",
        aiAssistant = "Assistant IA",
        settings = "Paramètres",
        adminDashboard = "Administration",
        offlineBanner = "Hors connexion — Vos ressources téléchargées restent accessibles",
        signInTitle = "Bienvenue sur prof+",
        signInSubtitle = "Rejoignez les enseignants et responsables d'établissements du Burkina Faso et d'Afrique.",
        signInGoogleBtn = "Continuer avec Google",
        signOutBtn = "Déconnexion",
        publishBtn = "Publier",
        cancelBtn = "Annuler",
        saveBtn = "Enregistrer",
        searchPlaceholder = "Rechercher...",
        noDataYet = "Aucun contenu publié pour le moment."
    )
}

val LocalAppCopy = compositionLocalOf { copyFor("fr") }
