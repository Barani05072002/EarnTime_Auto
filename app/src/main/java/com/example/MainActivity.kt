package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.presentation.dashboard.DashboardScreen
import com.example.ui.theme.EarnTimeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EarnTimeTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "dashboard",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("dashboard") {
                            DashboardScreen(
                                onNavigateToTasks = { navController.navigate("tasks") },
                                onNavigateToHabits = { navController.navigate("habits") },
                                onNavigateToApps = { navController.navigate("apps") },
                                onNavigateToStats = { navController.navigate("stats") }
                            )
                        }
                        composable("tasks") {
                            com.example.presentation.tasks.TasksScreen(
                                onAddTask = { navController.navigate("add_task") }
                            )
                        }
                        composable("add_task") {
                            com.example.presentation.tasks.AddTaskScreen(
                                onTaskAdded = { navController.popBackStack() }
                            )
                        }
                        composable("habits") {
                            com.example.presentation.habits.HabitsScreen()
                        }
                        composable("apps") {
                            com.example.presentation.apps.AppSelectionScreen()
                        }
                        composable("stats") {
                            com.example.presentation.stats.StatsScreen()
                        }
                    }
                }
            }
        }
    }
}
