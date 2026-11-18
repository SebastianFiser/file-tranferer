package org.dpdns.sebastianfiser.filetransferer.screens

import androidx.annotation.StringRes
import org.dpdns.sebastianfiser.filetransferer.R

enum class AppScreen(@StringRes val labelRes: Int) {
    Home(R.string.nav_home),
    History(R.string.nav_history),
    Settings(R.string.nav_settings),
    Help(R.string.nav_help)
}