package com.orion.voicelab.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.outlined.PhoneForwarded
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orion.voicelab.R
import com.orion.voicelab.data.PendingCallRequest
import com.orion.voicelab.data.PhoneCallRecord
import com.orion.voicelab.data.PhoneCallStatus
import com.orion.voicelab.data.PhoneContactEntry
import com.orion.voicelab.data.SystemTelephonyState
import com.orion.voicelab.data.VoiceLabUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PendingCallConfirmationCard(
    pendingCall: PendingCallRequest,
    hasCallPhonePermission: Boolean,
    onSelectCandidate: (PhoneContactEntry) -> Unit,
    onConfirmDirectCall: () -> Unit,
    onOpenDialer: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pending_phone_call_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (pendingCall.awaitingConfirmation) {
                        stringResource(R.string.phone_confirm_title)
                    } else {
                        stringResource(R.string.phone_disambiguation_title)
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = if (pendingCall.awaitingConfirmation) "AUTORIZACIÓN" else "ELIGE UNO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (pendingCall.awaitingConfirmation) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = pendingCall.contactName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = pendingCall.phoneNumber,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (pendingCall.reason.isNotBlank()) {
                        Text(
                            text = "Motivo: ${pendingCall.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (pendingCall.scheduledNote.isNotBlank()) {
                        Text(
                            text = "Nota: ${pendingCall.scheduledNote}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onConfirmDirectCall,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("confirm_phone_call_button"),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasCallPhonePermission) {
                                stringResource(R.string.phone_confirm_call_now)
                            } else {
                                "Autorizar y llamar"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("cancel_phone_call_button"),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.phone_cancel_call))
                    }
                }

                OutlinedButton(
                    onClick = onOpenDialer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("dialer_phone_call_button"),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Dialpad,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.phone_confirm_dialer))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pendingCall.candidates.forEachIndexed { idx, candidate ->
                        Surface(
                            onClick = { onSelectCandidate(candidate) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("disambiguate_candidate_$idx"),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${idx + 1}. ${candidate.displayName}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${candidate.phoneNumber} · ${candidate.label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.Call,
                                    contentDescription = stringResource(R.string.cd_call_contact),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("cancel_phone_call_button"),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(text = stringResource(R.string.phone_cancel_call))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneSectionScreen(
    state: VoiceLabUiState,
    onSearchQueryChanged: (String) -> Unit,
    onRequestContactsPermission: () -> Unit,
    onRequestCallPhonePermission: () -> Unit,
    onRequestPhoneStatePermission: () -> Unit,
    onPickNativeContact: () -> Unit,
    onToggleFavorite: (PhoneContactEntry) -> Unit,
    onRequestCallContact: (PhoneContactEntry) -> Unit,
    onPrepareManualCall: (name: String, number: String, reason: String, scheduledNote: String) -> Unit,
    onSavePreparedCallOnly: (name: String, number: String, reason: String, scheduledNote: String) -> Unit,
    onSelectCandidate: (PhoneContactEntry) -> Unit,
    onConfirmDirectCall: () -> Unit,
    onOpenDialer: () -> Unit,
    onCancelPendingCall: () -> Unit,
    onRedialRecord: (PhoneCallRecord) -> Unit,
    onUpdateRecordNotes: (id: Long, reason: String, userNotes: String, outcome: String, followUpPending: Boolean) -> Unit,
    onDeleteRecord: (Long) -> Unit
) {
    var showManualForm by rememberSaveable { mutableStateOf(false) }
    var manualName by rememberSaveable { mutableStateOf("") }
    var manualNumber by rememberSaveable { mutableStateOf("") }
    var manualReason by rememberSaveable { mutableStateOf("") }
    var manualSchedule by rememberSaveable { mutableStateOf("") }
    var editingRecord by remember { mutableStateOf<PhoneCallRecord?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 640.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "phone_header") {
            PhoneStatusBannerCard(
                state = state,
                onRequestContactsPermission = onRequestContactsPermission,
                onRequestCallPhonePermission = onRequestCallPhonePermission,
                onRequestPhoneStatePermission = onRequestPhoneStatePermission
            )
        }

        val pending = state.pendingCallRequest
        if (pending != null) {
            item(key = "phone_pending_confirm") {
                PendingCallConfirmationCard(
                    pendingCall = pending,
                    hasCallPhonePermission = state.hasCallPhonePermission,
                    onSelectCandidate = onSelectCandidate,
                    onConfirmDirectCall = onConfirmDirectCall,
                    onOpenDialer = onOpenDialer,
                    onCancel = onCancelPendingCall
                )
            }
        }

        item(key = "phone_search_and_picker") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.phoneSearchQuery,
                        onValueChange = onSearchQueryChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("phone_search_input"),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.cd_search_contacts)
                            )
                        },
                        trailingIcon = {
                            if (state.phoneSearchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = { onSearchQueryChanged("") },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.action_cancel)
                                    )
                                }
                            }
                        },
                        placeholder = {
                            Text(text = stringResource(R.string.phone_search_hint))
                        }
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onPickNativeContact,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("pick_native_contact_button"),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Contacts,
                                contentDescription = stringResource(R.string.cd_pick_contact),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.phone_pick_native))
                        }

                        if (!state.hasContactsPermission) {
                            OutlinedButton(
                                onClick = onRequestContactsPermission,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("request_contacts_permission_button"),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(text = stringResource(R.string.phone_allow_contacts))
                            }
                        }

                        OutlinedButton(
                            onClick = { showManualForm = !showManualForm },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("toggle_manual_call_form_button"),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.EventNote,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.phone_manual_title))
                        }
                    }

                    AnimatedVisibility(visible = showManualForm) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            OutlinedTextField(
                                value = manualName,
                                onValueChange = { manualName = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("phone_manual_name_input"),
                                singleLine = true,
                                label = { Text(stringResource(R.string.phone_manual_name_label)) }
                            )
                            OutlinedTextField(
                                value = manualNumber,
                                onValueChange = { manualNumber = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("phone_manual_number_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                label = { Text(stringResource(R.string.phone_manual_number_label)) }
                            )
                            OutlinedTextField(
                                value = manualReason,
                                onValueChange = { manualReason = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("phone_manual_reason_input"),
                                singleLine = true,
                                label = { Text(stringResource(R.string.phone_manual_reason_label)) }
                            )
                            OutlinedTextField(
                                value = manualSchedule,
                                onValueChange = { manualSchedule = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("phone_manual_schedule_input"),
                                singleLine = true,
                                label = { Text(stringResource(R.string.phone_manual_schedule_label)) }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onSavePreparedCallOnly(manualName, manualNumber, manualReason, manualSchedule)
                                        manualReason = ""
                                        manualSchedule = ""
                                    },
                                    enabled = manualNumber.any { it.isDigit() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("save_prepared_call_button"),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = stringResource(R.string.phone_action_prepare),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Button(
                                    onClick = {
                                        onPrepareManualCall(manualName, manualNumber, manualReason, manualSchedule)
                                    },
                                    enabled = manualNumber.any { it.isDigit() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("request_manual_call_button"),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = stringResource(R.string.phone_action_request_call),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.favoritePhoneContacts.isNotEmpty()) {
            item(key = "phone_favorites_header") {
                Text(
                    text = stringResource(R.string.phone_favorites_header, state.favoritePhoneContacts.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item(key = "phone_favorites_strip") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = state.favoritePhoneContacts,
                        key = { it.contactKey }
                    ) { fav ->
                        FavoriteContactChipCard(
                            entry = fav,
                            onCallClick = { onRequestCallContact(fav) },
                            onRemoveFavorite = { onToggleFavorite(fav) }
                        )
                    }
                }
            }
        }

        if (state.phoneContactMatches.isNotEmpty()) {
            item(key = "phone_matches_header") {
                Text(
                    text = stringResource(R.string.phone_results_header, state.phoneContactMatches.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(
                items = state.phoneContactMatches.take(15),
                key = { "match_${it.contactKey}" }
            ) { contact ->
                PhoneContactResultRow(
                    entry = contact,
                    onToggleFavorite = { onToggleFavorite(contact) },
                    onPrepareForm = {
                        manualName = contact.displayName
                        manualNumber = contact.phoneNumber
                        showManualForm = true
                    },
                    onRequestCall = { onRequestCallContact(contact) }
                )
            }
        }

        item(key = "phone_records_header") {
            Text(
                text = stringResource(R.string.phone_records_header, state.phoneCallRecords.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (state.phoneCallRecords.isEmpty()) {
            item(key = "phone_records_empty") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = stringResource(R.string.phone_records_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(
                items = state.phoneCallRecords,
                key = { "call_rec_${it.id}" }
            ) { record ->
                PhoneCallRecordCard(
                    record = record,
                    onRedial = { onRedialRecord(record) },
                    onEditNotes = { editingRecord = record },
                    onDelete = { onDeleteRecord(record.id) }
                )
            }
        }
    }

    editingRecord?.let { target ->
        EditCallNotesDialog(
            record = target,
            onDismiss = { editingRecord = null },
            onSave = { reason, userNotes, outcome, followUpPending ->
                onUpdateRecordNotes(target.id, reason, userNotes, outcome, followUpPending)
                editingRecord = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhoneStatusBannerCard(
    state: VoiceLabUiState,
    onRequestContactsPermission: () -> Unit,
    onRequestCallPhonePermission: () -> Unit,
    onRequestPhoneStatePermission: () -> Unit
) {
    val stateColor = when (state.systemTelephonyState) {
        SystemTelephonyState.OFFHOOK -> MaterialTheme.colorScheme.secondary
        SystemTelephonyState.RINGING -> MaterialTheme.colorScheme.tertiary
        SystemTelephonyState.IDLE -> MaterialTheme.colorScheme.primary
        SystemTelephonyState.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("phone_status_banner"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.phone_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.phone_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = stateColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, stateColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(stateColor)
                        )
                        Text(
                            text = state.systemTelephonyState.displayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = stateColor
                        )
                    }
                }
            }

            if (state.phoneStatusMessage.isNotBlank()) {
                Text(
                    text = state.phoneStatusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!state.isTelephonySupported) {
                Text(
                    text = stringResource(R.string.phone_no_telephony_hw),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = stringResource(R.string.phone_audio_limit_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!state.hasContactsPermission || !state.hasCallPhonePermission || !state.hasPhoneStatePermission) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!state.hasContactsPermission) {
                        FilterChip(
                            selected = false,
                            onClick = onRequestContactsPermission,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Security,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(stringResource(R.string.phone_allow_contacts)) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                    if (!state.hasCallPhonePermission) {
                        FilterChip(
                            selected = false,
                            onClick = onRequestCallPhonePermission,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.PhoneForwarded,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(stringResource(R.string.phone_allow_call)) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                    if (!state.hasPhoneStatePermission) {
                        FilterChip(
                            selected = false,
                            onClick = onRequestPhoneStatePermission,
                            label = { Text(stringResource(R.string.phone_allow_state)) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteContactChipCard(
    entry: PhoneContactEntry,
    onCallClick: () -> Unit,
    onRemoveFavorite: () -> Unit
) {
    Surface(
        modifier = Modifier.widthIn(min = 170.dp, max = 230.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onRemoveFavorite,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = stringResource(R.string.cd_favorite_contact),
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = entry.phoneNumber,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            FilledTonalButton(
                onClick = onCallClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.agenda_call_action))
            }
        }
    }
}

@Composable
private fun PhoneContactResultRow(
    entry: PhoneContactEntry,
    onToggleFavorite: () -> Unit,
    onPrepareForm: () -> Unit,
    onRequestCall: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = if (entry.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(R.string.cd_favorite_contact),
                    tint = if (entry.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${entry.phoneNumber} · ${entry.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onPrepareForm,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.EventNote,
                    contentDescription = stringResource(R.string.phone_action_prepare),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            FilledTonalButton(
                onClick = onRequestCall,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = stringResource(R.string.cd_call_contact),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(R.string.agenda_call_action))
            }
        }
    }
}

@Composable
private fun PhoneCallRecordCard(
    record: PhoneCallRecord,
    onRedial: () -> Unit,
    onEditNotes: () -> Unit,
    onDelete: () -> Unit
) {
    val badgeColor = when (record.status) {
        PhoneCallStatus.COMPLETED, PhoneCallStatus.IN_PROGRESS, PhoneCallStatus.CALL_INITIATED ->
            MaterialTheme.colorScheme.secondary
        PhoneCallStatus.PREPARED, PhoneCallStatus.AWAITING_CONFIRMATION ->
            MaterialTheme.colorScheme.tertiary
        PhoneCallStatus.DIALING_UI_OPENED ->
            MaterialTheme.colorScheme.primary
        PhoneCallStatus.CANCELLED, PhoneCallStatus.FAILED ->
            MaterialTheme.colorScheme.error
    }
    val dateStr = remember(record.createdAtMs) {
        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(record.createdAtMs))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("phone_record_${record.id}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.contactName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${record.phoneNumber} · $dateStr" +
                            if (record.durationSeconds > 0L) " · ${record.durationSeconds}s" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = badgeColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = record.status.displayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (record.reason.isNotBlank()) {
                Text(
                    text = "Motivo: ${record.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (record.scheduledNote.isNotBlank()) {
                Text(
                    text = "Agenda: ${record.scheduledNote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (record.outcome.isNotBlank()) {
                Text(
                    text = "Resultado: ${record.outcome}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (record.userNotes.isNotBlank()) {
                Text(
                    text = "Notas: ${record.userNotes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (record.followUpPending) {
                Text(
                    text = "Seguimiento o devolución pendiente",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onRedial,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .minimumInteractiveComponentSize(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.phone_redial),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onEditNotes,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .minimumInteractiveComponentSize(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(R.string.phone_notes_edit))
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.cd_delete_call_record),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun EditCallNotesDialog(
    record: PhoneCallRecord,
    onDismiss: () -> Unit,
    onSave: (reason: String, userNotes: String, outcome: String, followUpPending: Boolean) -> Unit
) {
    var reason by remember { mutableStateOf(record.reason) }
    var outcome by remember { mutableStateOf(record.outcome) }
    var userNotes by remember { mutableStateOf(record.userNotes) }
    var followUpPending by remember { mutableStateOf(record.followUpPending) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${stringResource(R.string.phone_edit_notes_title)} · ${record.contactName}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.phone_manual_reason_label)) }
                )
                OutlinedTextField(
                    value = outcome,
                    onValueChange = { outcome = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.phone_notes_outcome_label)) }
                )
                OutlinedTextField(
                    value = userNotes,
                    onValueChange = { userNotes = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text(stringResource(R.string.phone_notes_body_label)) }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.phone_notes_followup_toggle),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    Switch(
                        checked = followUpPending,
                        onCheckedChange = { followUpPending = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(reason, userNotes, outcome, followUpPending) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )
}
