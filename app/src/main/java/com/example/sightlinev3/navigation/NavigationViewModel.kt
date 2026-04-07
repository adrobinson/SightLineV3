package com.example.sightlinev3.navigation

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sightlinev3.llm.LlmService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class HintState {
    object Idle: HintState()
    object Loading: HintState()
    data class Success(val text: String): HintState()
    data class Error(val message: String): HintState()
}

class NavigationViewModel (
    private val llmService: LlmService
) : ViewModel() {

    private val _hintState = MutableStateFlow<HintState>(HintState.Idle)
    val hintState: StateFlow<HintState> = _hintState

    /**
     * Call the functions in LLM Service
     */

    fun describeEnvironment(image: Bitmap, routeContext: String?){
        viewModelScope.launch {
            _hintState.value = HintState.Loading
            _hintState.value = try {
                HintState.Success(llmService.describeEnvironment(image, routeContext))
            } catch (e: Exception) {
                HintState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun describeWithQuery(image: Bitmap, userQuery: String, routeContext: String?){
        viewModelScope.launch {
            _hintState.value = HintState.Loading
            _hintState.value = try {
                HintState.Success(llmService.describeWithUserQuery(image, userQuery, routeContext))
            } catch (e: Exception) {
                HintState.Error(e.message ?: "Unknown Error")
            }
        }
    }

    fun describeAtCheckpoint(image: Bitmap, routeContext: String) {
        viewModelScope.launch {
            _hintState.value = HintState.Loading
            _hintState.value = try {
                HintState.Success(llmService.describeAtCheckpoint(image, routeContext))
            } catch (e: Exception){
                HintState.Error(e.message ?: "Unknown Error")
            }
        }
    }
}