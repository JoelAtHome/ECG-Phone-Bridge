package com.example.polarh10bridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
internal fun SourcePickerDialog(
    selected: SourceKind,
    onSelect: (SourceKind) -> Unit,
    onDismissRequest: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = UiWhite,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Source device",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = TextDark,
                )
                Text(
                    text = "Choose Polar or Feather. Find on the data path connects that kind.",
                    fontSize = 12.sp,
                    color = TextDark.copy(alpha = 0.65f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                SourceKind.entries.forEach { kind ->
                    val sel = kind == selected
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = sel,
                                    onClick = { onSelect(kind) },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = sel,
                            onClick = null,
                            colors =
                                RadioButtonDefaults.colors(
                                    selectedColor = BannerRed,
                                ),
                        )
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(
                                text = kind.displayName(),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = TextDark,
                            )
                            Text(
                                text = kind.pickerSubtitle(),
                                fontSize = 12.sp,
                                color = TextDark.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Close", color = BannerRed)
                    }
                }
            }
        }
    }
}

@Composable
internal fun FeatherConnectingDialog(
    phase: String,
    detail: String,
    onCancel: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val isError = phase == "Error"
    val line = featherHumanPhase(phase, detail)
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = UiWhite,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isError) "Feather" else "Finding Feather",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = TextDark,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isError) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.size(12.dp))
                    }
                    Text(
                        text = line.ifBlank { if (isError) "Feather connect failed" else "Working…" },
                        color = TextDark,
                        fontSize = 14.sp,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (isError) {
                        TextButton(onClick = onDismissRequest) {
                            Text("Close", color = BannerRed)
                        }
                    } else {
                        TextButton(onClick = onCancel) {
                            Text("Cancel", color = BannerRed)
                        }
                    }
                }
            }
        }
    }
}
