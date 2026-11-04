package com.waivera.waivera.core.designsystem.component

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.designsystem.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Picks a calendar day. The Material picker counts days as UTC midnights, so the day goes in
 * and comes out as a plain [LocalDate] and never passes through the device zone; callers give
 * days in the employee's saved zone. [latest] and [earliest] bound the choice when set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaiveraDatePickerDialog(
    initial: LocalDate,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    earliest: LocalDate? = null,
    latest: LocalDate? = null,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toUtcMillis(),
        selectableDates = remember(earliest, latest) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val day = utcTimeMillis.toUtcDate()
                    return (earliest == null || !day.isBefore(earliest)) && (latest == null || !day.isAfter(latest))
                }

                override fun isSelectableYear(year: Int): Boolean =
                    (earliest == null || year >= earliest.year) && (latest == null || year <= latest.year)
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPicked(it.toUtcDate()) }
                onDismiss()
            }) { Text(stringResource(R.string.core_designsystem_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_designsystem_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

/** Picks a wall-clock time; the caller places it in the employee's saved zone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaiveraTimePickerDialog(initial: LocalTime, title: String, onPicked: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = {
                onPicked(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) { Text(stringResource(R.string.core_designsystem_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_designsystem_cancel)) }
        },
    ) {
        TimePicker(state = state)
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
