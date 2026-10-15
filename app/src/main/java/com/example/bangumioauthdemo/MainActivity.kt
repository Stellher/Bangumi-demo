package com.example.bangumioauthdemo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val oauth: OAuthViewModel by viewModels()
    private val explorer: ApiExplorerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OAuthSessionManager.initialize(application)
        if (OAuthSessionManager.session.value != null) RefreshScheduler.schedule(application)
        setContent {
            MaterialTheme {
                AppShell(oauth, explorer, ::openAuth, ::openUrl)
            }
        }
        oauth.callback(intent?.data)
    }

    override fun onStart() {
        super.onStart()
        if (OAuthSessionManager.session.value != null) {
            lifecycleScope.launch { OAuthSessionManager.ensureValidToken() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        oauth.callback(intent.data)
    }

    private fun openAuth(url: String) {
        try {
            OAuthBrowserLauncher.launch(this, url)
        } catch (e: Exception) {
            oauth.browserLaunchFailed(e.message ?: "打开系统授权窗口失败")
        }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@Composable
private fun AppShell(
    oauth: OAuthViewModel,
    explorer: ApiExplorerViewModel,
    openAuth: (String) -> Unit,
    openUrl: (String) -> Unit
) {
    var page by remember { mutableIntStateOf(0) }
    val session by oauth.session.collectAsStateWithLifecycle()
    val oauthBusy by oauth.busy.collectAsStateWithLifecycle()
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = page == 0,
                    onClick = { page = 0 },
                    icon = { Text("◉") },
                    label = { Text("OAuth 授权") }
                )
                NavigationBarItem(
                    selected = page == 1,
                    onClick = { page = 1 },
                    icon = { Text("⌘") },
                    label = { Text("全部 API") }
                )
                NavigationBarItem(
                    selected = page == 2,
                    onClick = { page = 2 },
                    icon = { Text("≡") },
                    label = { Text("请求记录") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                0 -> OAuthScreen(oauth, openAuth, openUrl)
                1 -> ApiScreen(explorer, session?.accessToken, { page = 0 })
                else -> HistoryScreen(explorer)
            }
            if (oauthBusy && page == 0) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun PageHeadline(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OAuthScreen(
    vm: OAuthViewModel,
    openAuth: (String) -> Unit,
    openUrl: (String) -> Unit
) {
    val secret by vm.clientSecret.collectAsStateWithLifecycle()
    val output by vm.output.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val refreshState by vm.refreshState.collectAsStateWithLifecycle()
    var showTokens by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PageHeadline("Bangumi OAuth", "用户授权信息、Token 生命周期与全部 API 接口调试")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (session == null) "● 尚未授权" else "● 已获取 Access Token",
                color = if (session == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text("Android 本地演示", style = MaterialTheme.typography.labelSmall)
        }
        LabeledValue("Client ID", BuildConfig.BGM_CLIENT_ID, copy = true)
        LabeledValue("Redirect URI", OAuthRepository.REDIRECT, copy = true)
        OutlinedTextField(
            value = secret,
            onValueChange = vm::updateClientSecret,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("App Secret（仅保存在本机）") },
            supportingText = { Text("本地 Demo 无法保护 Client Secret；请勿将带密钥的 APK 发布。") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Button(
            onClick = { openAuth(vm.beginLogin()) },
            enabled = !busy && secret.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("通过系统安全授权窗口登录 Bangumi") }
        Text(
            "本应用不使用 WebView，也不会获取你的 Bangumi 账号密码。登录与授权确认由系统浏览器安全处理；" +
                "返回授权码后，App 使用 OkHttp 完成 Token 交换与自动续期。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = vm::inspectAuthorizationEntry,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("用 OkHttp 检查授权入口 HTTP 跳转（无账号凭据）") }
        Text(
            "这只检测未登录请求的 HTTP 状态与重定向路径，不抓取系统浏览器的登录 Cookie、密码或验证码。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (session != null) {
            val current = session!!
            HorizontalDivider()
            Text("授权凭据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { showTokens = !showTokens }) {
                Text(if (showTokens) "隐藏敏感 Token" else "显示 Access / Refresh Token")
            }
            LabeledValue("Access Token", if (showTokens) current.accessToken else "••••••••", copy = showTokens)
            LabeledValue("Refresh Token", if (showTokens) current.refreshToken else "••••••••", copy = showTokens)
            LabeledValue("Token Type", current.tokenType)
            LabeledValue("User ID", current.userId)
            LabeledValue("Scope", current.scope)
            LabeledValue("expires_in", current.expiresIn.toString() + " 秒")
            LabeledValue("预计过期", DateFormat.getDateTimeInstance().format(Date(current.expiresAtMillis)))
            Text("Token 已加密保存，重启 App 无需重新登录；API 请求自动使用最新 Bearer Token。",
                style = MaterialTheme.typography.bodySmall)
        }
        LabeledValue("自动续期状态", refreshState.message)
        Text("自动刷新：有效期不足 24 小时时尝试续期；WorkManager 约每 12 小时检查一次（受系统后台调度限制）。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = vm::refresh, enabled = !busy && session != null, modifier = Modifier.weight(1f)) {
                Text("刷新 Token")
            }
            OutlinedButton(onClick = vm::status, enabled = !busy && session != null, modifier = Modifier.weight(1f)) {
                Text("Token 状态")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::me, enabled = !busy && session != null) { Text("当前用户 /v0/me") }
            TextButton(onClick = vm::clear) { Text("清除所有凭据") }
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("OAuth 接口响应", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { clipboard.setText(AnnotatedString(output)) }) { Text("复制 JSON") }
        }
        JsonPanel(output)
        Text("OAuth 端点：GET /oauth/authorize；POST /oauth/access_token（授权码 / 刷新）；POST /oauth/token_status。",
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { openUrl("https://github.com/bangumi/api/blob/master/docs-raw/How-to-Auth.md") }) {
            Text("打开官方 OAuth 文档 ↗")
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String, copy: Boolean = false) {
    val clipboard = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SelectionContainer {
                Text(value.ifBlank { "—" }, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (copy) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(value)) }) { Text("复制") }
            }
        }
    }
}

@Composable
private fun ApiScreen(vm: ApiExplorerViewModel, token: String?, goToOAuth: () -> Unit) {
    val selected by vm.selected.collectAsStateWithLifecycle()
    if (selected == null) ApiListScreen(vm, token, goToOAuth)
    else ApiDetailsScreen(vm, selected!!, token, goToOAuth)
}

@Composable
private fun ApiListScreen(vm: ApiExplorerViewModel, token: String?, goToOAuth: () -> Unit) {
    var search by remember { mutableStateOf("") }
    var chosenTag by remember { mutableStateOf("全部") }
    var chosenMethod by remember { mutableStateOf("全部") }
    val tags = remember(vm.endpoints) { listOf("全部") + vm.endpoints.map { it.tag }.distinct() }
    val methods = listOf("全部", "GET", "POST", "PUT", "PATCH", "DELETE")
    val filtered = remember(search, chosenTag, chosenMethod) {
        vm.endpoints.filter {
            (chosenTag == "全部" || it.tag == chosenTag) &&
            (chosenMethod == "全部" || it.method == chosenMethod) &&
            (search.isBlank() ||
                it.path.contains(search.trim(), ignoreCase = true) ||
                it.summary.contains(search.trim(), ignoreCase = true) ||
                it.operationId.contains(search.trim(), ignoreCase = true) ||
                it.description.contains(search.trim(), ignoreCase = true))
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            PageHeadline("全部 API · ${vm.endpoints.size}", "官方 OpenAPI 快照 · 支持方法、参数、鉴权与响应信息")
            Spacer(Modifier.height(12.dp))
            Text(if (token.isNullOrBlank()) "尚未登录：可调试公开接口" else "OAuth 已连接：API 请求会自动携带 Bearer Token",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
            if (token.isNullOrBlank()) {
                TextButton(onClick = goToOAuth) { Text("去授权登录 →") }
            }
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("搜索 API、路径或 operationId") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = tag == chosenTag,
                        onClick = { chosenTag = tag },
                        label = { Text(tag) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                methods.forEach { method ->
                    FilterChip(selected = method == chosenMethod, onClick = { chosenMethod = method },
                        label = { Text(method) })
                }
            }
            Text("共找到 ${filtered.size} 个接口 · 写入操作需二次确认",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(filtered, key = { it.id }) { endpoint ->
            OutlinedCard(onClick = { vm.select(endpoint) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MethodLabel(endpoint.method)
                        Text(endpoint.tag, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (endpoint.auth == "required") {
                            Text("需授权", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (endpoint.mutatesData) {
                            Text("写入", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Text(endpoint.summary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Text(endpoint.path, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
        }
        item {
            Text("范围：Bangumi 官方新公开 /v0/ API + 官方列出的旧版兼容 API。新网站私有 Session API 不接受第三方 OAuth Token，不在列表中。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MethodLabel(method: String) {
    val color = when (method) {
        "GET" -> MaterialTheme.colorScheme.primary
        "POST" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Text(method, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
}

@Composable
private fun ApiDetailsScreen(
    vm: ApiExplorerViewModel,
    ep: ApiEndpoint,
    token: String?,
    goToOAuth: () -> Unit
) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val currentResult by vm.result.collectAsStateWithLifecycle()
    val values = remember(ep.id) {
        mutableStateMapOf<String, String>().apply {
            ep.params.forEach { p -> this[p.name] = if (p.required) p.example else "" }
        }
    }
    var jsonBody by remember(ep.id) { mutableStateOf(ep.requestBody?.example.orEmpty()) }
    var showSchema by remember(ep.id) { mutableStateOf(false) }
    var confirm by remember(ep.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    fun send() = vm.execute(ep, values.toMap(), jsonBody, token)
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("确认执行写入接口？") },
            text = {
                Text("${ep.method} ${ep.path}\n\n该请求可能创建、修改或删除你在 Bangumi 上的真实数据。确定发送？")
            },
            confirmButton = {
                Button(onClick = { confirm = false; send() }) { Text("确认发送") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("取消") }
            }
        )
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        TextButton(onClick = vm::backToList) { Text("← 返回全部 API") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MethodLabel(ep.method)
            Text(ep.tag, style = MaterialTheme.typography.labelMedium)
            Text(ep.auth.let { when (it) {
                "required" -> "必须授权"
                "optional" -> "授权可选"
                else -> "公开"
            } }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        PageHeadline(ep.summary, ep.path)
        if (ep.description.isNotBlank()) {
            Text(ep.description, style = MaterialTheme.typography.bodyMedium)
        }
        LabeledValue("Operation ID", ep.operationId)
        LabeledValue("规范来源", ep.source + if (ep.deprecated) " · 已弃用" else "")
        if (ep.scopes.isNotEmpty()) {
            LabeledValue("OpenAPI 权限标记", ep.scopes.joinToString(", "))
            Text("提示：Bangumi OAuth 文档注明 scope 参数尚未实现；以上是接口规范标记，不保证所有 Token 都具有写入权限。",
                style = MaterialTheme.typography.bodySmall)
        }
        if (ep.auth == "required" && token.isNullOrBlank()) {
            Text("该接口需要用户授权。", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = goToOAuth) { Text("前往 OAuth 登录") }
        }
        HorizontalDivider()
        Text("请求参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (ep.params.isEmpty()) Text("该接口无路径、查询或头部参数", style = MaterialTheme.typography.bodySmall)
        ep.params.forEach { p ->
            OutlinedTextField(
                value = values[p.name].orEmpty(),
                onValueChange = { values[p.name] = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("${p.name} · ${p.location}" + if (p.required) " *" else "") },
                placeholder = { if (p.example.isNotBlank()) Text(p.example) },
                supportingText = {
                    Text(buildString {
                        append(p.type)
                        if (p.description.isNotBlank()) append(" · " + p.description)
                        if (p.enumValues.isNotEmpty()) append("\n枚举: " + p.enumValues.joinToString(", "))
                        if (p.type == "array") append("\n多个值用英文逗号分隔")
                    })
                },
                singleLine = p.description.isBlank()
            )
        }
        ep.requestBody?.let { body ->
            HorizontalDivider()
            Text("请求体 ${body.mediaType}" + if (body.required) " *" else "",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (body.description.isNotBlank()) Text(body.description, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = jsonBody,
                onValueChange = { jsonBody = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                label = { Text("JSON 请求体（可编辑）") },
                minLines = 4
            )
            TextButton(onClick = { showSchema = !showSchema }) {
                Text(if (showSchema) "收起请求体 Schema" else "查看完整请求体 Schema")
            }
            if (showSchema) JsonPanel(body.schema)
        }
        Button(
            onClick = { if (ep.mutatesData) confirm = true else send() },
            enabled = !busy && (ep.auth != "required" || !token.isNullOrBlank()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "请求执行中…" else if (ep.mutatesData) "执行写入请求（需确认）" else "发送 API 请求")
        }
        HorizontalDivider()
        Text("官方响应码", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (ep.responses.isEmpty()) Text("规范中未列出响应码", style = MaterialTheme.typography.bodySmall)
        ep.responses.forEach { r ->
            Text("${r.status} · ${r.description}" +
                (if (r.contentTypes.isEmpty()) "" else " · ${r.contentTypes.joinToString()}"),
                style = MaterialTheme.typography.bodySmall)
        }
        val outcome = currentResult?.takeIf { it.endpointId == ep.id }
        if (outcome != null) {
            HorizontalDivider()
            Text("实际响应", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            outcome.error?.let { Text("请求失败：$it", color = MaterialTheme.colorScheme.error) }
            outcome.response?.let { r ->
                Text("HTTP ${r.httpCode} · ${r.durationMillis} ms · ${r.byteCount} bytes" +
                    if (r.truncated) " · 响应截断至 1 MB" else "",
                    color = if (r.httpCode in 200..299) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error)
                LabeledValue("请求 URL", r.url, copy = true)
                if (r.headers.isNotBlank()) LabeledValue("响应 Headers", r.headers, copy = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("响应 Body", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { clipboard.setText(AnnotatedString(r.content)) }) { Text("复制响应") }
                }
                JsonPanel(r.content)
            }
        }
    }
}

@Composable
private fun JsonPanel(content: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        SelectionContainer {
            Text(content.ifBlank { "—" }, modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HistoryScreen(vm: ApiExplorerViewModel) {
    val history by vm.history.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeadline("请求记录", "只记录本次运行的 API 调用；不会存储 Access Token 或 Secret")
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = vm::clearHistory) { Text("清空记录") }
        }
        if (history.isEmpty()) item { Text("暂无请求记录") }
        items(history) { entry ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(entry.label, style = MaterialTheme.typography.titleSmall)
                    Text(if (entry.code == null) "失败" else "HTTP ${entry.code} · ${entry.duration} ms",
                        color = if (entry.code != null && entry.code in 200..299)
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    SelectionContainer {
                        Text(entry.detail, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
