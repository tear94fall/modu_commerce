package com.example.moducommerce.feature.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moducommerce.R
import com.example.moducommerce.core.ui.components.EmptyBox
import com.example.moducommerce.core.ui.components.ErrorBox
import com.example.moducommerce.core.ui.components.LoadingBox
import com.example.moducommerce.core.ui.theme.BrandRed
import com.example.moducommerce.core.ui.theme.ProductSurface

@Composable
fun CategoryScreen(
    onOpenCategory: (Long, String) -> Unit,
    viewModel: CategoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when {
        state.loading -> LoadingBox()
        state.error -> ErrorBox(message = stringResource(R.string.products_failed), onRetry = viewModel::load)
        state.roots.isEmpty() -> EmptyBox(message = stringResource(R.string.category_empty))
        else -> Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.width(120.dp).fillMaxHeight().background(ProductSurface)) {
                items(state.roots, key = { it.id }) { root ->
                    val selected = root.id == state.selectedRootId
                    Text(
                        text = root.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected) MaterialTheme.colorScheme.background else ProductSurface)
                            .clickable { viewModel.selectRoot(root.id) }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        color = if (selected) BrandRed else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            val root = state.selectedRoot
            if (root != null) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = root.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                    HorizontalDivider()
                    LazyColumn {
                        item {
                            ChildRow(name = stringResource(R.string.category_all)) { onOpenCategory(root.id, root.name) }
                        }
                        items(root.children, key = { it.id }) { child ->
                            ChildRow(name = child.name) { onOpenCategory(child.id, child.name) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChildRow(name: String, onClick: () -> Unit) {
    Text(
        text = name,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
    HorizontalDivider()
}
