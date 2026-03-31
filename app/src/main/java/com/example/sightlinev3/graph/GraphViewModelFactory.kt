package com.example.sightlinev3.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
   ViewModel factories are best practice for Android applications,
   the GraphViewModel will now be tied to the activity lifecycle
   (Created - Started - Resumed - Paused - Stopped - Destroyed)
 */

class GraphViewModelFactory(
    private val repository: GraphRepository
): ViewModelProvider.Factory {

    override fun <T: ViewModel> create(modelClass: Class<T>) : T {
        if (modelClass.isAssignableFrom(GraphViewModel::class.java)) {
            return GraphViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel Class")
    }
}