package com.example.presentation.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AddTaskScreen(
    onTaskAdded: () -> Unit,
    viewModel: TasksViewModel = viewModel(factory = TasksViewModel.Factory)
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var reward by remember { mutableStateOf("10") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Add Task",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Task Title") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        OutlinedTextField(
            value = reward,
            onValueChange = { reward = it },
            label = { Text("Reward (Credits)") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                viewModel.addTask(
                    title = title,
                    description = description,
                    reward = reward.toIntOrNull() ?: 10
                )
                onTaskAdded()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = title.isNotBlank()
        ) {
            Text("Save Task")
        }
    }
}
