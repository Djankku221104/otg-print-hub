package com.otgprinthub.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Detection : Screen("detection")
    data object DriverManager : Screen("driver_manager")
    data object FilePicker : Screen("file_picker")
    data object PrintPreview : Screen("print_preview/{fileUri}/{fileType}") {
        fun createRoute(fileUri: String, fileType: String) = "print_preview/$fileUri/$fileType"
    }
    data object PrintQueue : Screen("print_queue")
    data object PrinterDetails : Screen("printer_details/{printerId}") {
        fun createRoute(printerId: Long) = "printer_details/$printerId"
    }
    data object Diagnostics : Screen("diagnostics")
    data object Settings : Screen("settings")
}
