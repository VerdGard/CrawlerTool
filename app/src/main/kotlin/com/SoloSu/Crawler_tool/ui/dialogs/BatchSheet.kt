package com.SoloSu.Crawler_tool.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.SoloSu.Crawler_tool.ui.components.Dimens

/**
 * 批量抓取面板。
 *
 * 用 ModalBottomSheet 而不是 Dialog:表单有 3 个输入框,
 * 底部面板能随键盘上推且高度自适应,不会像居中对话框那样被压缩到需要内部滚动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchSheet(
    initialPattern: String,
    hint: String,
    onDismiss: () -> Unit,
    onStart: (pattern: String, start: Int, end: Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var pattern by rememberSaveable { mutableStateOf(initialPattern) }
    var startText by rememberSaveable { mutableStateOf("1") }
    var endText by rememberSaveable { mutableStateOf("5") }
    var patternError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.PageHorizontal)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "批量抓取",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "URL 模板用 {page} 占位,逐页请求后合并结果",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = pattern,
                onValueChange = {
                    pattern = it
                    patternError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("URL 模板") },
                placeholder = { Text("https://example.com/list?page={page}") },
                singleLine = true,
                isError = patternError != null,
                supportingText = patternError?.let { { Text(it) } },
                shape = MaterialTheme.shapes.small,
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it.filter(Char::isDigit) },
                    modifier = Modifier.weight(1f),
                    label = { Text("起始页") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it.filter(Char::isDigit) },
                    modifier = Modifier.weight(1f),
                    label = { Text("结束页") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val p = pattern.trim()
                        val start = startText.toIntOrNull() ?: 1
                        val end = endText.toIntOrNull() ?: start
                        when {
                            p.isBlank() || !p.contains("{page}") ->
                                patternError = "URL 模板需包含 {page} 占位符"
                            end < start ->
                                patternError = "结束页不能小于起始页"
                            else -> onStart(p, start, end)
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .width(160.dp)
                        .height(48.dp),
                ) {
                    Text("开始抓取")
                }
            }
        }
    }
}
