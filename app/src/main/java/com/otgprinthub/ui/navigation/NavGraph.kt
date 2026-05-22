package com.otgprinthub.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.otgprinthub.ui.detection.PrinterDetectionScreen
import com.otgprinthub.ui.diagnostics.DiagnosticsScreen
import com.otgprinthub.ui.driver.DriverManagerScreen
import com.otgprinthub.ui.filepicker.FilePickerScreen
import com.otgprinthub.ui.home.HomeScreen
import com.otgprinthub.ui.preview.PrintPreviewScreen
import com.otgprinthub.ui.printerdetails.PrinterDetailsScreen
import com.otgprinthub.ui.queue.PrintQueueScreen
import com.otgprinthub.ui.settings.SettingsScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        composable(Screen.Detection.route) {
            PrinterDetectionScreen(navController = navController)
        }
        composable(Screen.DriverManager.route) {
            DriverManagerScreen(navController = navController)
        }
        composable(Screen.FilePicker.route) {
            FilePickerScreen(navController = navController)
        }
        composable(
            route = Screen.PrintPreview.route,
            arguments = listOf(
                navArgument("fileUri") { type = NavType.StringType },
                navArgument("fileType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val fileUri = backStackEntry.arguments?.getString("fileUri") ?: ""
            val fileType = backStackEntry.arguments?.getString("fileType") ?: "UNKNOWN"
            PrintPreviewScreen(
                navController = navController,
                fileUri = fileUri,
                fileType = fileType
            )
        }
        composable(Screen.PrintQueue.route) {
            PrintQueueScreen(navController = navController)
        }
        composable(
            route = Screen.PrinterDetails.route,
            arguments = listOf(navArgument("printerId") { type = NavType.LongType })
        ) { backStackEntry ->
            val printerId = backStackEntry.arguments?.getLong("printerId") ?: 0L
            PrinterDetailsScreen(navController = navController, printerId = printerId)
        }
        composable(Screen.Diagnostics.route) {
            DiagnosticsScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
    }
}
