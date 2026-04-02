package com.example.sightlinev3.llm

import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.content

interface LlmService {

    val model get() = Firebase.ai(backend = GenerativeBackend.googleAI())
        .generativeModel(
            modelName = "gemini-2.5-flash",
            generationConfig = generationConfig {
                temperature = 0.3f
                maxOutputTokens = 200
            },
            systemInstruction = content {
                text("""
                    You are a navigation assistant for blind and visually impaired users.
                    Describe surroundings clearly using non-visual spatial references.
                    Be concise — no more than 3 sentences.
                    Never say 'I can see' — instead say 'In front of you' or 'In the left of the frame'.
                    Don't use any formatting characters
                """.trimIndent())
            }
        )

    /**
     * Use case 1: Camera only
     */
    suspend fun describeEnvironment(image: Bitmap): String {
        return try {
            val response = model.generateContent(
                content {
                    image(image)
                    text("Describe any objects or hazards that are directly in front of the user")
                }
            )
            response.text ?: "No description available"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     *  Use case 2: Camera + Text-to-Speech
     */
    suspend fun describeWithUserQuery(image: Bitmap, userQuery: String): String {
        return try {
            val response = model.generateContent(
                content{
                    image(image)
                    text("The user asked \"$userQuery\". Answer using what you can see in the image")
                }
            )
            response.text ?: "No Response"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * Use case 3: route mode QR scan
     */
    suspend fun describeAtCheckpoint(image: Bitmap, nodeId: String): String {
        return try {
            val response = model.generateContent(
                content {
                    image(image)
                    text("""
                        QR code $nodeId identified. This is the next checkpoint on the user's route.
                        Describe any objects or hazards in front of the user.
                    """.trimIndent())
                }
            )
            response.text ?: "No Response"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }


}