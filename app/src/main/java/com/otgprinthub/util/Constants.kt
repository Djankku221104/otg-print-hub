package com.otgprinthub.util

object Constants {
    const val ACTION_USB_PERMISSION = "com.otgprinthub.USB_PERMISSION"

    const val GITHUB_DRIVER_DB_URL =
        "https://raw.githubusercontent.com/otgprinthub/drivers/main/drivers.json"

    const val OPENPRINTING_BASE_URL = "https://www.openprinting.org/"
    const val FOOMATIC_BASE_URL = "https://raw.githubusercontent.com/OpenPrinting/foomatic-db/master/"

    const val USB_TRANSFER_TIMEOUT_MS = 5000
    const val USB_TRANSFER_CHUNK_SIZE = 16384
    const val USB_MAX_RETRIES = 3

    const val PPD_CACHE_DIR = "ppd"
    const val DRIVER_CACHE_DIR = "drivers"

    const val DB_NAME = "otg_print_hub.db"

    const val PREF_DEFAULT_PAPER_SIZE = "default_paper_size"
    const val PREF_DEFAULT_QUALITY = "default_quality"
    const val PREF_DEFAULT_COLOR_MODE = "default_color_mode"
    const val PREF_AUTO_CONNECT = "auto_connect"
    const val PREF_AUTO_DOWNLOAD_DRIVERS = "auto_download_drivers"
    const val PREF_DRIVER_REPO_URL = "driver_repo_url"
    const val PREF_THEME = "app_theme"

    const val USB_CLASS_PRINTER = 7

    const val MAX_IMAGE_SIZE_PX = 4096
    const val MAX_PDF_PAGES = 200
    const val MAX_MEMORY_BYTES = 200 * 1024 * 1024  // 200 MB

    const val PRINT_NOTIFICATION_CHANNEL = "print_jobs_channel"
    const val PRINT_NOTIFICATION_ID = 1001
}
