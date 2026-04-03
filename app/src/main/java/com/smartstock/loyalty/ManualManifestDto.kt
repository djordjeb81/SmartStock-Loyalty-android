package com.smartstock.loyalty

data class ManualManifestDto(
    val version: Int,
    val fileName: String,
    val pdfPath: String,
    val title: String? = "Uputstvo"
)