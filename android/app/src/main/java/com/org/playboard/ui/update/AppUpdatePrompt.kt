package com.org.playboard.ui.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.org.playboard.ui.theme.BrandLime

@Composable
fun AppUpdatePrompt(viewModel: AppUpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val ready = state as? AppUpdateState.ReadyToInstall
    LaunchedEffect(ready?.file) {
        ready?.let { result ->
            runCatching { launchInstaller(context, result.file) }
                .onSuccess { viewModel.markHandedOff() }
                .onFailure { viewModel.installFailed(result.file, it.message ?: "Android could not open the installer. Try again.") }
        }
    }
    when (val current = state) {
        is AppUpdateState.Available -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Update available") },
            text = { Text("Playboard ${current.update.versionName} is ready to install.") },
            confirmButton = { Button(onClick = { viewModel.downloadUpdate(current.update) }) { Text("Update") } },
            dismissButton = { TextButton(onClick = viewModel::dismiss) { Text("Later") } },
        )
        is AppUpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading update") },
            text = { Text("${current.progress}%") },
            confirmButton = { CircularProgressIndicator(color = BrandLime) },
        )
        is AppUpdateState.Checking -> Unit
        is AppUpdateState.Error -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Update unavailable") },
            text = { Text(current.message) },
            confirmButton = { TextButton(onClick = viewModel::checkForUpdate) { Text("Retry") } },
            dismissButton = { TextButton(onClick = viewModel::dismiss) { Text("Close") } },
        )
        else -> Unit
    }
}

private fun launchInstaller(context: Context, file: java.io.File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (error: ActivityNotFoundException) {
        throw IllegalStateException("No package installer is available.", error)
    } catch (error: SecurityException) {
        throw IllegalStateException("Enable Install unknown apps for Playboard in Android settings.", error)
    }
}
