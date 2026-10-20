package org.dpdns.sebastianfiser.filetransferer.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelScope
import kotlinx.coroutines.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class viewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<TransferUiState>(TransferUiSTate.Idle)
    val uiState: StateFlow<TransferUiState> = _uiSTate.asStateFlow()
}
