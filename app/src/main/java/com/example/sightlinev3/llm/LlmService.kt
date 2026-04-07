package com.example.sightlinev3.llm

import android.graphics.Bitmap
import android.util.Log
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
            },
            systemInstruction = content {
                text("""
                    You are a navigation assistant for blind and visually impaired users.
                    Describe surroundings clearly using non-visual spatial references.
                    Be concise — no more than 4 sentences.
                    Never say 'I can see' — instead say 'In front of you' or 'In the left of the frame'.
                    Don't use any formatting characters
                """.trimIndent())
            }
        )

    /**
     * Use case 1: Camera only
     */
    suspend fun describeEnvironment(image: Bitmap, routeContext: String?): String {
        Log.d("LLM SERVICE", "Request Sent")
        return try {
            /**
             * Tailors prompt to give context if the user is currently navigating a route or not
             */
            val prompt = if (routeContext != null){
                """
                    The user is navigating. ${routeContext}
                    look for the visual cue and point out any immediate hazards
                """.trimIndent()
            } else {
                "Describe any objects or hazards in front of the user, aswell as any doorways/entrances"
            }
            Log.d("USER_PROMPT",prompt)
            val response = model.generateContent(
                content {
                    image(image)
                    text(prompt)
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
    suspend fun describeWithUserQuery(image: Bitmap, userQuery: String, routeContext: String?): String {
        return try {
            val prompt = if (routeContext != null) {
                """
                    The user is navigating. $routeContext
                    The user asked: $userQuery
                    Answer using what you can see in the image, keeping the route in mind
                """.trimIndent()
            } else {
                "The user asked \"$userQuery\". Answer using what you can see in the image"
            }
            Log.d("USER_PROMPT",prompt)
            val response = model.generateContent(
                content{
                    image(image)
                    text(prompt)
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
    suspend fun describeAtCheckpoint(image: Bitmap, routeContext: String): String {
        return try {
            val response = model.generateContent(
                content {
                    image(image)
                    text("""
                        $routeContext
                        look for the visual cue and point out any immediate hazards
                    """.trimIndent())
                }
            )
            response.text ?: "No Response"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }


}