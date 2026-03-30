package com.example.sightlinev3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.sightlinev3.graph.GraphRepository
import com.example.sightlinev3.graph.GraphViewModel

class MainActivity : ComponentActivity() {

    private val viewModel by lazy {
        val repo = GraphRepository(this)
        GraphViewModel(repo)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GraphScreen {
                val path = viewModel.runPathfinding("01", "07")
                println(path)
            }
        }
    }
}

// OnRunPathfinding: () 'No Args' -> Unit 'returns Unit'
@Composable
fun GraphScreen(onRunPathfinding: () -> Unit) {
    Column {
        Button(onClick = { onRunPathfinding() }) { // Passing callbacks, this runs whatever function is passed into it
            Text("Run Pathfinding")
        }
    }
}