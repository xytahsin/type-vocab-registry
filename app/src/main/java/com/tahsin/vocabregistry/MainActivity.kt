package com.tahsin.vocabregistry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.*
import com.tahsin.vocabregistry.domain.SessionMode
import com.tahsin.vocabregistry.ui.AppViewModel
import com.tahsin.vocabregistry.ui.screens.*
import com.tahsin.vocabregistry.ui.theme.Ledger
import com.tahsin.vocabregistry.ui.theme.VocabTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Root(vm) }
    }
}

@Composable
fun Root(vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    VocabTheme(ui.themeMode) {
        when {
            ui.loading -> StarryLoader()

            !ui.calibrated -> CalibrationScreen(vm)

            else -> {
                val nav = rememberNavController()
                Scaffold(
                    containerColor = Ledger.Bg,
                    bottomBar = {
                        val current = nav.currentBackStackEntryAsState().value?.destination?.route
                        if (current?.startsWith("session") != true) {
                            NavigationBar(containerColor = Ledger.BgSoft) {
                                listOf(
                                    "home" to "Docket", "progress" to "Progress", "browse" to "Registry",
                                    "writing" to "Writing", "settings" to "Settings",
                                ).forEach { (route, label) ->
                                    NavigationBarItem(
                                        selected = current == route,
                                        onClick = { nav.navigate(route) { popUpTo("home"); launchSingleTop = true } },
                                        icon = {},
                                        label = { Text(label, fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedTextColor = Ledger.Brass,
                                            unselectedTextColor = Ledger.GreenSoft,
                                            indicatorColor = Color.Transparent,
                                        ),
                                    )
                                }
                            }
                        }
                    },
                ) { pad ->
                    NavHost(nav, startDestination = "home", Modifier.padding(pad)) {
                        composable("home") { DashboardScreen(vm) { mode -> nav.navigate("session/${mode.name}") } }
                        composable("progress") { ProgressScreen(vm) }
                        composable("browse") { BrowseScreen(vm) }
                        composable("writing") { WritingScreen(vm) }
                        composable("settings") { SettingsScreen(vm) }
                        composable("session/{mode}") { back ->
                            val mode = SessionMode.valueOf(back.arguments?.getString("mode") ?: "DEEP")
                            SessionScreen(vm, mode) { nav.popBackStack() }
                        }
                    }
                }
            }
        }
    }
}
