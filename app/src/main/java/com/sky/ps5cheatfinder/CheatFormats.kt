package com.sky.ps5cheatfinder

/** MC4/SHN export is intentionally not implemented until verified sample formats are available. */
object CheatFormats {
    fun cheatFileName(titleId: String, version: String, extension: String): String = "${titleId}_${version}.$extension"
}
