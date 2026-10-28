package com.drivemate.showcase.feature.chat

import com.drivemate.showcase.messaging.chatNotificationId
import com.drivemate.showcase.messaging.OpenChatThread
import com.drivemate.showcase.core.util.buildOneToOneChatThreadId
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import android.content.Context
import android.app.NotificationManager
import com.drivemate.showcase.ui.patterns.DmConfirmDialog
import com.drivemate.showcase.core.util.isMessageRead
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import com.drivemate.showcase.navigation.LocalFloatingNavInset
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Edit
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import com.drivemate.showcase.ui.profileInitials
import com.drivemate.showcase.ui.LocalReduceMotion
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import com.drivemate.showcase.ui.dsv2.DmAdaptiveTopBar
import com.drivemate.showcase.navigation.floatingNavContentPadding
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import com.drivemate.showcase.ui.dsv2.DmPressKind
import com.drivemate.showcase.ui.dsv2.dmClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.R
import com.drivemate.showcase.components.DMEmptyState
import com.drivemate.showcase.components.DMErrorState
import com.drivemate.showcase.components.DMLoadingState
import com.drivemate.showcase.core.model.ChatContact
import com.drivemate.showcase.core.model.ChatMessage
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.ds.DmCard
import com.drivemate.showcase.ui.ds.DriveMateTopBar
import com.drivemate.showcase.ui.ds.InlineAlert
import com.drivemate.showcase.ui.ds.InlineAlertKind
import kotlinx.datetime.TimeZone

@Composable
fun ChatInboxScreen(
    vm: ChatInboxViewModel,
    onBack: () -> Unit,
    onOpenThread: (uid: String, name: String) -> Unit,
    /** Aksi tambahan di bilah atas (mis. lonceng Expat). Default null = perilaku lama untuk Driver/Admin. */
    topBarActions: (@Composable RowScope.() -> Unit)? = null
) {
    val state by vm.uiState.collectAsState()
    val zone = TimeZone.of("Asia/Jakarta")
    val errorRes = state.errorRes
    // Sistem Back menutup daftar kontak dulu sebelum keluar dari layar.
    BackHandler(enabled = state.showContacts) { vm.setShowContacts(false) }

    Scaffold(
        topBar = {
            DmAdaptiveTopBar(
                title = stringResource(
                    if (state.showContacts) R.string.chat_new_message else R.string.chat_inbox_title
                ),
                onBack = if (state.showContacts) ({ vm.setShowContacts(false) }) else onBack,
                actions = topBarActions ?: {}
            )
        },
        floatingActionButton = {
            if (!state.showContacts) {
                // Slot FAB Scaffold berada di luar Column ber-`testTagsAsResourceId`, jadi perlu sendiri.
                Box(Modifier.semantics { testTagsAsResourceId = true }) {
                    ExtendedFloatingActionButton(
                        onClick = { vm.setShowContacts(true) },
                        icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        text = { Text(stringResource(R.string.chat_new_message)) },
                        modifier = Modifier
                            .testTag(ChatMaestroTags.NEW_MESSAGE)
                            .padding(bottom = LocalFloatingNavInset.current)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }
                .testTag(ChatMaestroTags.CONTACTS_ROOT)
                .padding(padding)
        ) {
            if (state.showContacts) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::onQueryChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ChatMaestroTags.SEARCH_FIELD)
                        .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                    placeholder = { Text(stringResource(R.string.chat_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = floatingNavContentPadding(
                    PaddingValues(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                when {
                    // Daftar kontak: status direktori sendiri (tidak ikut menahan daftar thread).
                    state.showContacts && state.contactsLoading -> item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            contentAlignment = Alignment.Center
                        ) { DMLoadingState(message = stringResource(R.string.state_loading_message)) }
                    }

                    state.showContacts && state.contactsErrorRes != null -> item {
                        DMErrorState(message = stringResource(state.contactsErrorRes!!), onAction = vm::refresh)
                    }

                    !state.showContacts && state.isLoading -> item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            contentAlignment = Alignment.Center
                        ) { DMLoadingState(message = stringResource(R.string.state_loading_message)) }
                    }

                    !state.showContacts && errorRes != null -> item {
                        Box(Modifier.testTag(ChatMaestroTags.INBOX_ERROR)) {
                            DMErrorState(message = stringResource(errorRes), onAction = vm::refresh)
                        }
                    }

                    state.showContacts -> {
                        val groups = groupContacts(filterContacts(state.contacts, state.query))
                        if (groups.isEmpty()) {
                            item {
                                DMEmptyState(
                                    title = stringResource(R.string.chat_contacts_empty_title),
                                    message = stringResource(R.string.chat_contacts_empty_message)
                                )
                            }
                        }
                        groups.forEach { (role, members) ->
                            item(key = "group-$role") {
                                Text(
                                    text = stringResource(role.toChatRoleLabelRes()),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = AppSpacing.sm)
                                )
                            }
                            items(members, key = { it.uid }) { contact ->
                                ChatContactCard(contact = contact, onClick = {
                                    onOpenThread(contact.uid, contact.displayName)
                                })
                            }
                        }
                    }

                    state.threads.isEmpty() -> item {
                        DMEmptyState(
                            title = stringResource(R.string.chat_threads_empty_title),
                            message = stringResource(R.string.chat_threads_empty_message)
                        )
                    }

                    else -> items(state.threads, key = { it.summary.threadId }) { row ->
                        ChatThreadRowCard(
                            row = row,
                            myUid = state.myUid,
                            zone = zone,
                            onClick = { onOpenThread(row.summary.partnerUid, row.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatThreadRowCard(
    row: ChatThreadRow,
    myUid: String?,
    zone: TimeZone,
    onClick: () -> Unit
) {
    val s = row.summary
    val unread = s.unreadCount > 0
    val preview = when {
        s.lastMessageDeleted -> stringResource(R.string.chat_deleted_placeholder)
        s.lastMessageSenderUid != null && s.lastMessageSenderUid == myUid ->
            stringResource(R.string.chat_preview_you) + s.lastMessagePreview
        else -> s.lastMessagePreview
    }
    val timeLabel = if (s.lastMessageAtMillis <= 0L) "" else {
        val today = chatEpochDay(System.currentTimeMillis(), zone.id)
        val day = chatEpochDay(s.lastMessageAtMillis, zone.id)
        when (chatDayKind(day, today)) {
            ChatDayKind.Today -> TimeUtil.formatHm(s.lastMessageAtMillis, zone)
            ChatDayKind.Yesterday -> stringResource(R.string.chat_day_yesterday)
            ChatDayKind.Date -> TimeUtil.formatYmdFromEpochDay(day.toInt(), zone)
        }
    }
    DmCard(
        modifier = Modifier
            .fillMaxWidth()
            .dmClickable(onClick = onClick, role = SemanticsRole.Button, kind = DmPressKind.Meaningful)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profileInitials(row.name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontStyle = if (s.lastMessageDeleted) FontStyle.Italic else FontStyle.Normal
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (unread) {
                    Text(
                        text = if (s.unreadCount > 99) "99+" else s.unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatThreadScreen(
    vm: ChatThreadViewModel,
    onBack: () -> Unit
) {
    val state by vm.uiState.collectAsState()
    val errorRes = state.errorRes
    val zone = TimeZone.of("Asia/Jakarta")
    // Thread ini sedang terbuka (RESUMED): push chat untuk thread yang sama tidak ditampilkan (badge cukup),
    // dan notifikasi lama thread ini dibersihkan.
    val openThreadId = remember(state.currentUserUid, state.partnerUid) {
        state.currentUserUid?.let { runCatching { buildOneToOneChatThreadId(it, state.partnerUid) }.getOrNull() }
    }
    val chatLifecycleOwner = LocalLifecycleOwner.current
    val appContext = LocalContext.current.applicationContext
    DisposableEffect(chatLifecycleOwner, openThreadId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    OpenChatThread.threadId = openThreadId
                    openThreadId?.let {
                        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.cancel(it, chatNotificationId(it))
                        nm.cancel(it, 0) // notifikasi yang ditampilkan sistem (FCM) di background memakai id 0
                    }
                }
                Lifecycle.Event.ON_PAUSE -> if (OpenChatThread.threadId == openThreadId) OpenChatThread.threadId = null
                else -> Unit
            }
        }
        chatLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            chatLifecycleOwner.lifecycle.removeObserver(observer)
            if (OpenChatThread.threadId == openThreadId) OpenChatThread.threadId = null
        }
    }
    val listState = rememberLazyListState()
    val items = remember(state.messages, state.currentUserUid) {
        buildChatListItems(state.messages, state.currentUserUid, zone.id, System.currentTimeMillis())
    }
    var actionTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var confirmDeleteAll by remember { mutableStateOf<ChatMessage?>(null) }
    val clipboard = LocalClipboardManager.current
    // Tandai terbaca saat thread terlihat dan tiap ada pesan masuk baru.
    val latestIncoming = state.messages
        .filter { it.senderUid != state.currentUserUid && !it.deleted }
        .maxOfOrNull { it.createdAtMillis } ?: 0L
    LaunchedEffect(latestIncoming, state.currentUserUid) { vm.markReadIfNeeded(latestIncoming) }
    // Bahasa baca dari peran/direktori (cermin server), BUKAN locale ponsel; locale hanya untuk string UI.
    val viewerLang = state.viewerLang.orEmpty()
    val featureActive = remember(state.messages) { state.messages.any { it.translationStatus != null } }
    // Jam untuk jendela "sedang diterjemahkan" (10 dtk): diperbarui sekali setelah jendela habis.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.messages) {
        nowMillis = System.currentTimeMillis()
        delay(TRANSLATION_PENDING_WINDOW_MILLIS)
        nowMillis = System.currentTimeMillis()
    }
    // Auto-scroll ke pesan terbaru saat jumlah item berubah.
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.scrollToItem(items.lastIndex)
    }

    Scaffold(
        topBar = {
            DmAdaptiveTopBar(
                title = state.partnerName,
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }
                .testTag(ChatMaestroTags.THREAD_ROOT)
                .padding(padding)
                .imePadding()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            when {
                errorRes != null && state.messages.isEmpty() -> {
                    DMErrorState(
                        message = stringResource(errorRes),
                        onAction = vm::refresh
                    )
                    Spacer(Modifier.weight(1f))
                }

                state.isLoading && state.messages.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        DMLoadingState(message = stringResource(R.string.state_loading_message))
                    }
                }

                state.messages.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                        ) {
                            DMEmptyState(
                                title = stringResource(R.string.chat_empty_start_title),
                                message = stringResource(R.string.chat_empty_message)
                            )
                            // Balasan cepat HANYA mengisi composer; pengguna tetap menekan kirim.
                            QuickReplies(onPick = vm::onDraftChanged)
                        }
                    }
                }

                else -> {
                    if (errorRes != null) {
                        InlineAlert(
                            kind = InlineAlertKind.Error,
                            message = stringResource(errorRes),
                            actionText = stringResource(R.string.chat_action_retry),
                            onAction = vm::refresh
                        )
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(items, key = { it.key }) { item ->
                            when (item) {
                                is ChatListItem.DateSeparator -> ChatDateSeparator(item, zone)
                                is ChatListItem.Bubble -> ChatBubble(
                                    item = item,
                                    zone = zone,
                                    partnerName = state.partnerName,
                                    partnerReadAt = state.partnerReadAtMillis,
                                    onLongPress = { actionTarget = item.message },
                                    display = chooseDisplayText(
                                        viewerLang, item.isMine, item.message, nowMillis, featureActive
                                    )
                                )
                            }
                        }
                    }
                }
            }

            val feedbackRes = state.feedbackRes
            if (feedbackRes != null) {
                InlineAlert(
                    kind = InlineAlertKind.Error,
                    message = stringResource(feedbackRes),
                    actionText = stringResource(R.string.chat_feedback_dismiss),
                    onAction = vm::dismissFeedback
                )
            }

            translationTargetHint(state.viewerLang, state.partnerLang)?.takeIf { featureActive }?.let { lang ->
                Text(
                    text = stringResource(R.string.chat_composer_hint_format, stringResource(langNameRes(lang))),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppSpacing.sm)
                )
            }
            ChatComposer(
                draft = state.draftMessage,
                isSending = state.isSending,
                onDraftChange = vm::onDraftChanged,
                onSend = vm::sendCurrentMessage
            )
        }
    }

    actionTarget?.let { target ->
        val mine = target.senderUid == state.currentUserUid
        MessageActionSheet(
            canCopy = !target.deleted,
            canDeleteForAll = mine && !target.deleted,
            onCopy = {
                clipboard.setText(AnnotatedString(target.body))
                actionTarget = null
            },
            onHideForMe = {
                vm.hideForMe(target)
                actionTarget = null
            },
            onDeleteForAll = {
                confirmDeleteAll = target
                actionTarget = null
            },
            onDismiss = { actionTarget = null }
        )
    }
    confirmDeleteAll?.let { target ->
        DmConfirmDialog(
            title = stringResource(R.string.chat_delete_all_title),
            message = stringResource(R.string.chat_delete_all_message),
            confirmLabel = stringResource(R.string.chat_delete_all_confirm),
            cancelLabel = stringResource(R.string.dm_logout_cancel),
            destructive = true,
            onConfirm = {
                vm.deleteForEveryone(target)
                confirmDeleteAll = null
            },
            onDismiss = { confirmDeleteAll = null }
        )
    }
}

/** Aksi pesan (tekan lama): Salin · Hapus untuk saya · Hapus untuk semua (hanya pesan sendiri). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionSheet(
    canCopy: Boolean,
    canDeleteForAll: Boolean,
    onCopy: () -> Unit,
    onHideForMe: () -> Unit,
    onDeleteForAll: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = AppSpacing.lg)) {
            if (canCopy) SheetAction(stringResource(R.string.chat_action_copy), onCopy)
            SheetAction(stringResource(R.string.chat_action_delete_for_me), onHideForMe)
            if (canDeleteForAll) {
                SheetAction(stringResource(R.string.chat_action_delete_for_all), onDeleteForAll, destructive = true)
            }
        }
    }
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit, destructive: Boolean = false) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .dmClickable(onClick = onClick, role = SemanticsRole.Button)
            .padding(horizontal = AppSpacing.lg, vertical = 14.dp)
    )
}

/** Composer pil: 1 baris yang tumbuh hingga 4 baris, tombol kirim bundar 48dp. */
@Composable
private fun ChatComposer(
    draft: String,
    isSending: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val canSend = draft.isNotBlank() && !isSending
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { onDraftChange(it.take(CHAT_MAX_CHARS)) },
            modifier = Modifier.weight(1f).testTag(ChatMaestroTags.COMPOSER_FIELD),
            supportingText = if (showCharCounter(draft.length)) {
                { Text(stringResource(R.string.chat_counter_format, draft.length, CHAT_MAX_CHARS)) }
            } else null,
            placeholder = { Text(stringResource(R.string.chat_field_message)) },
            shape = RoundedCornerShape(24.dp),
            minLines = 1,
            maxLines = 4,
            enabled = !isSending
        )
        val sendLabel = stringResource(
            if (isSending) R.string.chat_action_sending else R.string.chat_action_send
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (canSend) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .dmClickable(onClick = onSend, role = SemanticsRole.Button, enabled = canSend)
                .testTag(ChatMaestroTags.SEND_BUTTON)
                .semantics { contentDescription = sendLabel },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChatDateSeparator(item: ChatListItem.DateSeparator, zone: TimeZone) {
    val label = when (item.kind) {
        ChatDayKind.Today -> stringResource(R.string.chat_day_today)
        ChatDayKind.Yesterday -> stringResource(R.string.chat_day_yesterday)
        ChatDayKind.Date -> TimeUtil.formatYmdFromEpochDay(item.epochDay.toInt(), zone)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickReplies(onPick: (String) -> Unit) {
    val options = listOf(
        stringResource(R.string.chat_quick_reply_1),
        stringResource(R.string.chat_quick_reply_2),
        stringResource(R.string.chat_quick_reply_3)
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        options.forEach { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .dmClickable(onClick = { onPick(text) }, role = SemanticsRole.Button)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@StringRes
private fun langNameRes(lang: String): Int = if (lang == "ja") R.string.chat_lang_ja else R.string.chat_lang_id

@StringRes
private fun registerNameRes(register: String?): Int? = when (register) {
    "formal" -> R.string.chat_register_formal
    "polite" -> R.string.chat_register_polite
    "casual" -> R.string.chat_register_casual
    else -> null
}

@Composable
private fun ChatBubble(
    item: ChatListItem.Bubble,
    zone: TimeZone,
    partnerName: String,
    partnerReadAt: Long?,
    onLongPress: () -> Unit,
    display: DisplayText
) {
    val big = 18.dp
    val tail = 5.dp
    val joinsAbove = item.position == BubblePosition.Middle || item.position == BubblePosition.Last
    val joinsBelow = item.position == BubblePosition.Middle || item.position == BubblePosition.First
    // Sudut "ekor" 5dp: sisi pengirim pada bubble terakhir grup (dan sisi yang menyambung antar-bubble).
    val shape = if (item.isMine) {
        RoundedCornerShape(
            topStart = big, topEnd = if (joinsAbove) tail else big,
            bottomStart = big, bottomEnd = if (joinsBelow || item.showTime) tail else big
        )
    } else {
        RoundedCornerShape(
            topStart = if (joinsAbove) tail else big, topEnd = big,
            bottomStart = if (joinsBelow || item.showTime) tail else big, bottomEnd = big
        )
    }
    val container = if (item.isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val content = if (item.isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val muted = if (item.isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    var showOriginal by rememberSaveable(item.key) { mutableStateOf(false) }
    val chipHint = stringResource(R.string.chat_translation_original_hint)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (item.isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!item.isMine) {
            // Avatar lawan bicara 26dp hanya di bubble terakhir grup; lainnya spasi kosong agar sejajar.
            if (item.showTime) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profileInitials(partnerName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                Spacer(Modifier.size(26.dp))
            }
            Spacer(Modifier.width(6.dp))
        }
        Column(
            modifier = Modifier.fillMaxWidth(0.8f),
            horizontalAlignment = if (item.isMine) Alignment.End else Alignment.Start
        ) {
            Column(
                modifier = Modifier
                    .clip(shape)
                    .dmClickable(
                        onClick = { if (display.translated) showOriginal = !showOriginal },
                        onLongClick = onLongPress,
                        feedback = false
                    )
                    .background(container)
                    .then(
                        if (item.isMine) Modifier
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (item.message.deleted) {
                    Text(
                        text = stringResource(R.string.chat_deleted_placeholder),
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = muted
                    )
                } else if (display.state == TranslationUiState.Pending) {
                    Text(text = item.message.body, style = MaterialTheme.typography.bodyMedium, color = content)
                    TranslatingShimmer(color = muted)
                } else {
                    Text(text = display.primary, style = MaterialTheme.typography.bodyMedium, color = content)
                }
                if (display.translated && !item.message.deleted) {
                    val from = display.fromLang.orEmpty()
                    val registerRes = registerNameRes(display.register)
                    val chipText = if (registerRes != null) {
                        stringResource(
                            R.string.chat_translation_from_format,
                            stringResource(langNameRes(from)),
                            stringResource(registerRes)
                        )
                    } else {
                        stringResource(R.string.chat_translation_from_only_format, stringResource(langNameRes(from)))
                    }
                    Text(
                        text = "\uD83C\uDF10 $chipText",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                        modifier = Modifier
                            .testTag(ChatMaestroTags.TRANSLATION_CHIP)
                            .heightIn(min = 32.dp)
                            .dmClickable(
                                onClick = { showOriginal = !showOriginal },
                                role = SemanticsRole.Button,
                                onClickLabel = chipHint
                            )
                            .wrapContentHeight(Alignment.CenterVertically)
                    )
                    if (showOriginal) {
                        Text(
                            text = display.original.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = muted,
                            modifier = Modifier.dashedTopBorder(muted)
                        )
                    }
                    if (display.uncertain) {
                        Text(
                            text = "\u26A0 " + stringResource(R.string.chat_translation_uncertain),
                            style = MaterialTheme.typography.labelSmall,
                            color = muted
                        )
                    }
                }
                if (display.state == TranslationUiState.Failed) {
                    Text(
                        text = stringResource(R.string.chat_translation_failed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            if (item.showTime) {
                val sent = display.sentLang?.let {
                    stringResource(R.string.chat_translation_sent_format, stringResource(langNameRes(it)))
                }
                val time = TimeUtil.formatHm(item.message.createdAtMillis, zone)
                Row(
                    modifier = Modifier.padding(top = 2.dp, bottom = AppSpacing.xs, start = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (sent != null && !item.message.deleted) "$time · $sent" else time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.isMine && !item.message.deleted) {
                        val read = isMessageRead(item.message.createdAtMillis, partnerReadAt)
                        val statusLabel = stringResource(if (read) R.string.chat_status_read else R.string.chat_status_sent)
                        Text(
                            text = if (read) "\u2713\u2713" else "\u2713",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (read) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .testTag(if (read) ChatMaestroTags.STATUS_READ else ChatMaestroTags.STATUS_SENT)
                                .semantics { contentDescription = statusLabel }
                        )
                    }
                }
            }
        }
    }
}

/** Pengganti teks terjemahan selagi menunggu server: batang berdenyut (statis bila reduce-motion). */
@Composable
private fun TranslatingShimmer(color: Color) {
    val label = stringResource(R.string.chat_translation_pending)
    val reduce = LocalReduceMotion.current
    val alpha = if (reduce) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "translatingShimmer")
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "translatingShimmerAlpha"
        ).value
    }
    Box(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .height(10.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = alpha))
            .testTag(ChatMaestroTags.TRANSLATING)
            .semantics { contentDescription = label }
    )
}

/** Garis putus-putus di atas teks asli. */
private fun Modifier.dashedTopBorder(color: Color): Modifier = drawBehind {
    drawLine(
        color = color.copy(alpha = 0.6f),
        start = Offset(0f, 0f),
        end = Offset(size.width, 0f),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
    )
}.padding(top = 6.dp)

@Composable
private fun ChatContactCard(
    contact: ChatContact,
    onClick: () -> Unit
) {
    DmCard(
        modifier = Modifier
            .fillMaxWidth()
            .dmClickable(onClick = onClick, role = SemanticsRole.Button, kind = DmPressKind.Meaningful)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profileInitials(contact.displayName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(contact.role.toChatRoleLabelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@StringRes
private fun Role.toChatRoleLabelRes(): Int = when (this) {
    Role.ADMIN -> R.string.chat_role_admin
    Role.DRIVER -> R.string.chat_role_driver
    Role.EXPAT -> R.string.chat_role_expat
}
