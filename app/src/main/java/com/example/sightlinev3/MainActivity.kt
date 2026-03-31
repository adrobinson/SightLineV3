package com.example.sightlinev3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.sightlinev3.graph.GraphRepository
import com.example.sightlinev3.graph.GraphViewModel
import com.example.sightlinev3.graph.GraphViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: GraphViewModel by viewModels {
        GraphViewModelFactory(GraphRepository(this)) // Inject the repository dependency through the factory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GraphScreen {
                val path = viewModel.runPathfinding("01", "04")
                println(path)
            }
        }
    }
}

@Composable
fun GraphScreen(onRunPathfinding: () -> Unit) {
    Column {
        Button(onClick = { onRunPathfinding() }) { // Passing callbacks, this runs whatever function is passed into it
            Text("Run Pathfinding")
        }
    }
}