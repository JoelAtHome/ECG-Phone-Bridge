package com.example.polarh10bridge

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DiagramLineInactive = Color(0xFFBDBDBD)
private val HeartVibrant = Color(0xFFEF4444)
private val EcgActive = Color.White
private val H10GlowBg = Color(0xFFFFE4EC)
private val H10GlowBorder = Color(0xFFF06292)
private val FeatherGlowBg = Color(0xFFE0F2F1)
private val FeatherGlowBorder = Color(0xFF26A69A)
private val PhoneActiveBorder = Color(0xFF64B5F6)
private val PhoneIdleBorder = Color(0xFFE0E0E0)
private val PcGlowBorder = Color(0xFF42A5F5)
private val PcIdleBorder = Color(0xFFCFD8DC)
private val FlowRed = Color(0xFFC1121F)
private val SmallLabelGray = Color(0xFF616161)
private val DiagramTextDark = Color(0xFF1A1A1A)
private val NodeGrayTint = Color(0xFF9E9E9E)

@Composable
internal fun BridgeFlowDiagram(
    sourceKind: SourceKind,
    sourceLinked: Boolean,
    inProgressLine: String? = null,
    phoneIpv4: String? = null,
    pcBridgeConnected: Boolean,
    pcBridgeIp: String? = null,
    pcBridgeUserName: String?,
    pcClientApp: String? = null,
    onFindSource: () -> Unit,
    onChangeSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "bridgePulse")

    val heartPulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(550, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "heartPulse",
    )
    val heartColorPulse by infinite.animateFloat(
        initialValue = 0.88f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(540, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "heartColorPulse",
    )

    val pcHeartPulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(520, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pcHeartPulse",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Data path",
            fontSize = 12.sp,
            color = SmallLabelGray,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        val heartScale = heartPulse
        val heartFill = HeartVibrant.copy(alpha = heartColorPulse)
        val ecgColor = EcgActive.copy(alpha = 0.92f + (heartColorPulse - 0.88f))

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(78.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFF5F5F5))
                    .border(
                        width = 2.dp,
                        color = FlowRed.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(20.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .offset(x = (-1).dp)
                            .scale(heartScale),
                ) {
                    Image(
                        painter = painterResource(R.drawable.bridge_heart_fill),
                        contentDescription = null,
                        modifier = Modifier.size(50.dp),
                        colorFilter = ColorFilter.tint(heartFill),
                    )
                    Image(
                        painter = painterResource(R.drawable.bridge_heart_ecg),
                        contentDescription = null,
                        modifier = Modifier.size(50.dp),
                        colorFilter = ColorFilter.tint(ecgColor),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(0.5f.dp))
        VerticalFlowArrow(active = sourceLinked)

        val sourceGlowBg =
            if (sourceKind == SourceKind.Feather) FeatherGlowBg else H10GlowBg
        val sourceGlowBorder =
            if (sourceKind == SourceKind.Feather) FeatherGlowBorder else H10GlowBorder
        val sourceBg by animateColorAsState(
            targetValue = if (sourceLinked) sourceGlowBg else Color(0xFFFAFAFA),
            animationSpec = tween(380),
            label = "sourceBg",
        )
        val sourceBorder by animateColorAsState(
            targetValue = if (sourceLinked) sourceGlowBorder else DiagramLineInactive,
            animationSpec = tween(380),
            label = "sourceBd",
        )

        Button(
            onClick = onFindSource,
            modifier =
                Modifier
                    .fillMaxWidth(0.86f),
            shape = RoundedCornerShape(18.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = sourceBg,
                    contentColor = DiagramTextDark,
                ),
            border = androidx.compose.foundation.BorderStroke(3.dp, sourceBorder),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 7.dp, horizontal = 12.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter =
                        painterResource(
                            if (sourceKind == SourceKind.Feather) {
                                R.drawable.bridge_feather_box
                            } else {
                                R.drawable.bridge_h10_capsule
                            },
                        ),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(30.dp),
                    colorFilter = if (sourceLinked) null else ColorFilter.tint(NodeGrayTint),
                )
                Text(
                    text =
                        if (sourceLinked) {
                            "CONNECTED (tap to disconnect/rescan)"
                        } else {
                            "TAP TO FIND SENSORS"
                        },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (sourceLinked) FlowRed else DiagramTextDark,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Text(
            text = sourceKind.diagramCaption(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = DiagramTextDark,
            lineHeight = 10.sp,
            style = TextStyle(lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)),
        )
        if (!inProgressLine.isNullOrBlank()) {
            Text(
                text = inProgressLine,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = FlowRed,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
            )
        }
        TextButton(
            onClick = onChangeSource,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.defaultMinSize(minHeight = 28.dp),
        ) {
            Text(
                text = "Change ECG sensor",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = FlowRed,
                lineHeight = 12.sp,
                style =
                    TextStyle(
                        lineHeightStyle =
                            LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both,
                            ),
                    ),
            )
        }

        Spacer(modifier = Modifier.height(0.5f.dp))
        VerticalFlowArrow(active = sourceLinked)

        val phoneBorder by animateColorAsState(
            targetValue =
                if (pcBridgeConnected) PhoneActiveBorder else PhoneIdleBorder,
            animationSpec = tween(400),
            label = "phoneBd",
        )
        val phoneBg by animateColorAsState(
            targetValue =
                if (pcBridgeConnected) {
                    Color(0xFFE3F2FD).copy(alpha = 0.45f)
                } else {
                    Color(0xFFF5F5F5)
                },
            label = "phoneBg",
        )

        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(phoneBg)
                    .border(3.dp, phoneBorder, RoundedCornerShape(18.dp))
                    .padding(horizontal = 9.dp, vertical = 7.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.bridge_phone),
                contentDescription = null,
                modifier =
                    Modifier
                        .height(56.dp)
                        .align(Alignment.Center),
                colorFilter = if (pcBridgeConnected) null else ColorFilter.tint(NodeGrayTint),
            )
        }
        Text(
            text =
                buildString {
                    append("This phone: WiFi")
                    val ip = phoneIpv4?.trim().orEmpty()
                    if (ip.isNotEmpty()) {
                        append(" · ")
                        append(ip)
                    }
                },
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = DiagramTextDark,
            lineHeight = 11.sp,
            textAlign = TextAlign.Center,
            style = TextStyle(lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(0.5f.dp))
        VerticalFlowArrow(active = pcBridgeConnected)

        val pcBorder by animateColorAsState(
            targetValue = if (pcBridgeConnected) PcGlowBorder else PcIdleBorder,
            animationSpec = tween(400),
            label = "pcBd",
        )
        val pcBg by animateColorAsState(
            targetValue =
                if (pcBridgeConnected) {
                    Color(0xFFE1F5FE).copy(alpha = 0.55f)
                } else {
                    Color(0xFFF5F5F5)
                },
            label = "pcBg",
        )
        val pcHeartScale = if (pcBridgeConnected) pcHeartPulse else 1f

        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.86f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(pcBg)
                    .border(3.dp, pcBorder, RoundedCornerShape(18.dp))
                    .padding(7.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.bridge_pc),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .height(54.dp)
                            .fillMaxWidth(),
                    colorFilter = if (pcBridgeConnected) null else ColorFilter.tint(NodeGrayTint),
                )
                if (pcBridgeConnected) {
                    Image(
                        painter = painterResource(R.drawable.bridge_heart_fill),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .offset(x = (-26).dp, y = (-10).dp)
                                .size(16.dp)
                                .scale(pcHeartScale),
                        colorFilter = ColorFilter.tint(HeartVibrant),
                    )
                }
            }
        }
        Text(
            text =
                buildString {
                    append(pcHostDiagramLabel(pcBridgeConnected, pcClientApp))
                    val ip = pcBridgeIp?.trim().orEmpty()
                    if (ip.isNotEmpty()) {
                        append(" · ")
                        append(ip)
                    }
                },
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = DiagramTextDark,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth(),
        )
        if (pcBridgeConnected && !pcBridgeUserName.isNullOrBlank()) {
            Text(
                text = "User: $pcBridgeUserName",
                fontSize = 10.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Normal,
                color = SmallLabelGray,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .offset(y = (-4).dp),
            )
        }
    }
}

@Composable
private fun VerticalFlowArrow(active: Boolean) {
    val color by animateColorAsState(
        targetValue = if (active) FlowRed else DiagramLineInactive,
        animationSpec = tween(420),
        label = "flow",
    )
    Canvas(
        modifier =
            Modifier
                .height(22.dp)
                .fillMaxWidth(),
    ) {
        val mid = size.width / 2f
        val stroke = 6f
        drawLine(
            color = color,
            start = Offset(mid, 2f),
            end = Offset(mid, size.height - 14f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        val tri =
            Path().apply {
                moveTo(mid - 12f, size.height - 14f)
                lineTo(mid, size.height - 3f)
                lineTo(mid + 12f, size.height - 14f)
                close()
            }
        drawPath(tri, color)
    }
}

/** Label under the PC node — branded host until a specific app identifies itself. */
private fun pcHostDiagramLabel(
    connected: Boolean,
    clientApp: String?,
): String {
    if (!connected) return "PC: J. Kobe Host App"
    return when (clientApp?.trim()?.lowercase()) {
        "hertz_and_hearts", "hnh" -> "PC: Hertz & Hearts"
        "vns_ta" -> "PC: VNS-TA"
        "flaretracker" -> "PC: FlareTracker"
        null, "" -> "PC: J. Kobe Host App"
        else -> "PC: J. Kobe Host App"
    }
}
