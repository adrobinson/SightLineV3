package com.example.sightlinev3.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.sightlinev3.llm.LlmService
import com.example.sightlinev3.navigation.NavigationViewModel

class NavigationViewModelFactory(
    private val llmService: LlmService
): ViewModelProvider.Factory {

    override fun <T: ViewModel> create(modelClass: Class<T>) : T {
        if (modelClass.isAssignableFrom(NavigationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NavigationViewModel(llmService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel Class")
    }
}