package com.example.data.repository

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

enum class AiTaskMode(val labelFr: String, val labelEn: String, val instructionFr: String) {
    PREP_COURS(
        "Préparation de cours",
        "Lesson Planning",
        "Structure une fiche de préparation de cours claire (objectifs, prérequis, déroulement, évaluation formative)."
    ),
    SUGGESTIONS(
        "Suggestions pédagogiques",
        "Teaching Ideas",
        "Propose des méthodes actives, adaptées aux classes nombreuses et contextes locaux du Burkina Faso et d'Afrique."
    ),
    RESUME(
        "Résumé de document",
        "Document Summary",
        "Résume fidèlement les points clés du texte fourni sans rien inventer."
    ),
    EXPLICATION(
        "Explication de notion",
        "Concept Explanation",
        "Explique la notion de façon progressive avec des exemples concrets et accessibles aux élèves."
    ),
    ORGANISATION(
        "Organisation des idées",
        "Organize Ideas",
        "Organise les idées en un plan structuré et opérationnel."
    ),
    DEBAT(
        "Préparation de débat",
        "Debate Preparation",
        "Présente des arguments équilibrés (pour, contre, points d'attention) pour animer un débat professionnel."
    ),
    REFORMULATION(
        "Reformulation professionnelle",
        "Professional Rewriting",
        "Reformule le texte dans un style institutionnel clair, respectueux et professionnel."
    )
}

class AiAssistantRepository {
    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    Tu es l'assistant pédagogique intelligent de prof+ (produit par ONADJA PRODUCTION), le réseau professionnel des enseignants du Burkina Faso et d'Afrique.
                    Règles strictes :
                    1. Comprends et réponds en français ou en anglais selon la langue de l'utilisateur.
                    2. Ne jamais inventer de textes officiels, de décrets ministériels, de circulaires ou de références bibliographiques fictives. Si une référence officielle exacte n'est pas fournie par l'enseignant, précise qu'il convient de vérifier auprès des textes officiels du ministère compétent.
                    3. Sois clair, structuré, bienveillant et directement utile en classe.
                    """.trimIndent()
                )
            }
        )
    }

    fun streamResponse(mode: AiTaskMode, userPrompt: String, lang: String): Flow<String> = flow {
        val languageHint = if (lang == "en") "Reply in English." else "Réponds en français."
        val composedPrompt = """
            Mode demandé : ${mode.labelFr} (${mode.instructionFr})
            $languageHint

            Demande de l'enseignant(e) :
            $userPrompt
        """.trimIndent()

        val accumulator = StringBuilder()
        withTimeout(60_000L) {
            model.generateContentStream(composedPrompt).collect { chunk ->
                val chunkText = chunk.text.orEmpty()
                if (chunkText.isNotEmpty()) {
                    accumulator.append(chunkText)
                    emit(accumulator.toString())
                }
            }
        }
    }
}
