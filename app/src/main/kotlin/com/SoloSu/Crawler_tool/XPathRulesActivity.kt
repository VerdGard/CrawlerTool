package com.SoloSu.Crawler_tool

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.SoloSu.Crawler_tool.repository.SettingsRepository
import com.SoloSu.Crawler_tool.ui.components.MarkdownDocument
import com.SoloSu.Crawler_tool.ui.theme.CrawlerTheme

/**
 * XPath 语法速查。
 *
 * 内容仍是 assets/xpath_rules.md,渲染换成 Compose:
 * 不再手工拼 TextView,排版、代码块、表格都走同一套 M3 样式,日/夜切换即时生效。
 */
class XPathRulesActivity : BaseActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = SettingsRepository(this)

        setContent {
            val darkTheme = when (settings.getTheme()) {
                SettingsRepository.THEME_LIGHT -> false
                SettingsRepository.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }

            CrawlerTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.isMonetEnabled(),
            ) {
                val context = LocalContext.current
                // 文档只读一次,旋转屏幕也复用同一份解析结果
                val markdown = remember {
                    runCatching {
                        context.assets.open("xpath_rules.md")
                            .bufferedReader()
                            .use { it.readText() }
                    }.getOrElse { "加载失败:${it.message}" }
                }

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.surface,
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "XPath 规则",
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_chevron_left),
                                        contentDescription = "返回",
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    },
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            MarkdownDocument(markdown = markdown)
                        }
                    }
                }
            }
        }
    }
}
