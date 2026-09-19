package com.example.moducommerce.feature.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moducommerce.core.model.Category
import com.example.moducommerce.data.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryUiState(
    val roots: List<Category> = emptyList(),
    val selectedRootId: Long? = null,
    val loading: Boolean = true,
    val error: Boolean = false,
) {
    val selectedRoot: Category? get() = roots.firstOrNull { it.id == selectedRootId }
}

/** 카테고리 탭: 왼쪽 상위 목록, 오른쪽 하위 목록("전체" 포함). */
@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryUiState())
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            catalogRepository.categories()
                .onSuccess { roots -> _uiState.update { it.copy(roots = roots, selectedRootId = it.selectedRootId ?: roots.firstOrNull()?.id, loading = false) } }
                .onFailure { _uiState.update { it.copy(loading = false, error = true) } }
        }
    }

    fun selectRoot(id: Long) {
        _uiState.update { it.copy(selectedRootId = id) }
    }
}
