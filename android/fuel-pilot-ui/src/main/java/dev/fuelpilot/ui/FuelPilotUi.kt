package dev.fuelpilot.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

private val Graphite = Color(0xFF171512)
private val Gold = Color(0xFFE3A33C)
private val GoldText = Color(0xFFF0C46E)
private val White = Color(0xFFF7F2EA)
private val Muted = Color(0xFFB9AE9D)
private val Coral = Color(0xFFFF9A88)
private val Line = Color(0xFF393329)

/** Distances include the host's real station-access route, not straight-line distance. */
data class FuelStation(val id: String, val name: String, val distanceKm: Double?, val extraMinutes: Int?, val remainingAtStationKm: Double? = null)
data class FuelRouteUi(
    val rangeKm: Double? = null,
    val destinationKm: Double? = null,
    val recommended: FuelStation? = null,
    val nextAfterPassing: FuelStation? = null,
    val stations: List<FuelStation> = emptyList(),
    /** Why this station is better, from the existing app's ranking logic. */
    val recommendationReason: String? = null,
)
private fun valid(n: Double?) = n?.takeIf { it.isFinite() && it >= 0 }
private fun number(n: Double): String = if (n % 1.0 == 0.0) n.toLong().toString() else String.format(Locale.ROOT, "%.1f", n)
private fun km(n: Double?) = valid(n)?.let { "${number(it)}km" } ?: "확인 중"
private fun shortfall(range: Double?, distance: Double?): Double? {
    val r = valid(range) ?: return null; val d = valid(distance) ?: return null
    return (d - r).takeIf { it > 0 }
}

/**
 * Native Compose surface only. The host supplies its map and verified SDK events.
 * onRequestWaypoint is a request; send RouteAdded only after the navigation SDK confirms it.
 * Persist seenIds/preferences in the host ViewModel/SavedStateHandle before re-delivering events.
 */
@Composable
fun FuelPilotUi(
    state: FuelUiState,
    route: FuelRouteUi,
    onEvent: (FuelUiEvent) -> Unit,
    onRequestWaypoint: (String) -> Unit,
    modifier: Modifier = Modifier,
    mapContent: @Composable BoxScope.() -> Unit = {},
) {
    val parked = state.motion == Motion.Parked
    val showCharacter = parked && state.characterEnabled
    val station = route.recommended
    val select: (FuelStation) -> Unit = {
        if (state.pendingStation == null) {
            onEvent(FuelUiEvent.SelectStation(it.id))
            onRequestWaypoint(it.id)
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Gold, onPrimary = Color(0xFF241703), surface = Graphite, onSurface = White, background = Color(0xFF0C0B09))) {
        BoxWithConstraints(modifier.fillMaxSize()) {
            mapContent()
            // Detailed screens are suppressed if speed/parking state is moving or unknown.
            val screen = if (!parked && state.screen in listOf(Screen.Settings, Screen.LongDrive)) Screen.Widget else state.screen
            if (screen == Screen.Analysis) {
                Analysis(state, route, showCharacter, maxWidth < 840.dp, select, onEvent)
            } else if (screen == Screen.Settings) {
                Settings(state, onEvent)
            } else {
                Column(
                    Modifier.align(Alignment.BottomStart).padding(24.dp)
                        .widthIn(max = 540.dp).fillMaxWidth()
                        .heightIn(max = (maxHeight - 48.dp).coerceAtLeast(1.dp)).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    when (screen) {
                        Screen.Recommendation -> {
                            val reason = if (shortfall(route.rangeKm, route.destinationKm) != null) "목적지까지 가려면\n중간에 주유해야 해요" else "현재 경로의 주유 정보를\n확인해 주세요"
                            if (showCharacter) PodongIntro(reason) else Copy(reason, 24)
                            Panel {
                                StationCopy(station)
                                state.error?.let { Text(it, color = Coral, fontSize = 17.sp) }
                                Actions(station, select, onEvent)
                                TextButton(onClick = { onEvent(FuelUiEvent.Show(Screen.Analysis)) }) { Text("주유가 필요한 이유 보기 ›", color = Muted, fontSize = 18.sp) }
                            }
                        }
                        Screen.Passing -> Panel {
                            Text("지나치기 전에 확인하세요", color = Coral, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            val reason = if (shortfall(route.rangeKm, route.nextAfterPassing?.distanceKm) != null) "이곳을 지나면 다음 주유소까지\n연료가 부족할 수 있어요" else "앞 주유소와 다음 주유 기회를\n확인해 주세요"
                            Copy(reason, 27)
                            HorizontalDivider(color = Line)
                            StationCopy(station)
                            Actions(station, select, onEvent)
                            TextButton(onClick = { onEvent(FuelUiEvent.Show(Screen.Analysis)) }) { Text("다음 주유소까지 거리 보기 ›", color = Muted, fontSize = 18.sp) }
                        }
                        Screen.Adding, Screen.Added -> Panel {
                            Text(if (screen == Screen.Adding) "내비게이션에 전달했어요" else "✓ 내비게이션 경로가 바뀌었어요", color = GoldText, fontSize = 18.sp)
                            Copy(if (screen == Screen.Adding) "경로 추가를 확인하고 있어요" else "주유소로 안내할게요", 28)
                            Text(if (screen == Screen.Adding) "경로 재탐색 결과를 기다리고 있어요." else "내비게이션 경로를 따라가세요.", color = Muted, fontSize = 20.sp)
                            Primary("지도 보기", enabled = screen == Screen.Added) { onEvent(FuelUiEvent.Continue) }
                        }
                        Screen.Refuel -> {
                            state.celebration?.takeIf { showCharacter }?.let { CelebrationHeader(it, state.reduceMotion, onEvent) }
                            Panel {
                                Text("✓ 주유 완료", color = GoldText, fontSize = 19.sp)
                                Copy("연료를 채웠어요", 32)
                                val range = valid(state.refuel?.rangeKm)
                                if (range != null) { Text("${number(range)} km", color = GoldText, fontSize = 54.sp, fontWeight = FontWeight.Bold); Text("지금 연료로 갈 수 있는 예상 거리예요.", color = Muted, fontSize = 20.sp) }
                                else Text("차량의 주행 가능 거리를 확인 중이에요.", color = Muted, fontSize = 20.sp)
                                valid(state.refuel?.liters)?.takeIf { it > 0 }?.let { Text("오늘은 ${number(it)}L 먹었어요!", color = White, fontSize = 21.sp) }
                                Primary("지도 보기") { onEvent(FuelUiEvent.Continue) }
                            }
                        }
                        Screen.LongDrive -> {
                            if (showCharacter) PodongIntro("여행 준비 완료!")
                            Panel {
                                val enough = valid(route.rangeKm)?.let { r -> valid(route.destinationKm)?.let { d -> r >= d } } == true
                                Copy(if (enough) "이번 경로는 주유 없이\n갈 수 있을 것으로 예상돼요" else "출발 전 연료와 경로를\n확인해 주세요", 28)
                                Text("주행 가능 ${km(route.rangeKm)} · 목적지 ${km(route.destinationKm)}", color = Muted, fontSize = 20.sp)
                                Primary("지도 보기") { onEvent(FuelUiEvent.Continue) }
                            }
                        }
                        else -> Panel {
                            Text("FUEL PILOT", color = GoldText, fontSize = 17.sp)
                            val text = when { state.refuel != null -> "연료를 채웠어요"; state.routeStation != null -> "경유 주유소로 안내 중이에요"; station != null -> "다음 주유소 · ${station.name}"; else -> "주유 정보를 확인 중이에요" }
                            Copy(text, 25)
                            Text(if (state.refuel != null) "주행 가능 거리 ${km(state.refuel.rangeKm)}" else station?.let { "${km(it.distanceKm)} 앞" } ?: "차량과 경로 정보를 기다려요.", color = Muted, fontSize = 19.sp)
                            TextButton(onClick = { onEvent(FuelUiEvent.Show(Screen.Analysis)) }) { Text("자세히 보기 ›", color = GoldText, fontSize = 18.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Graphite, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
    }
}
@Composable private fun Copy(text: String, size: Int) { Text(text, color = White, fontSize = size.sp, lineHeight = (size * 1.4).sp, fontWeight = FontWeight.Bold) }
@Composable private fun Primary(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF241703))) { Text(text, fontSize = 23.sp, fontWeight = FontWeight.Bold) }
}
@Composable private fun StationCopy(station: FuelStation?) {
    Copy(station?.name ?: "주유소 정보를 확인 중이에요", 30)
    if (station != null) Text(buildString { append("${km(station.distanceKm)} 앞"); station.extraMinutes?.takeIf { it >= 0 }?.let { append(" · 경로에서 ${it}분 추가") } }, color = Muted, fontSize = 21.sp)
}
@Composable private fun Actions(station: FuelStation?, select: (FuelStation) -> Unit, onEvent: (FuelUiEvent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { station?.let(select) }, enabled = station != null, modifier = Modifier.weight(1.65f).heightIn(min = 88.dp), shape = RoundedCornerShape(16.dp)) { Text("여기서 주유하기", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Button(onClick = { onEvent(FuelUiEvent.Continue) }, modifier = Modifier.weight(1f).heightIn(min = 88.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2620), contentColor = White)) { Text("계속 가기", fontSize = 21.sp) }
    }
}
@Composable private fun PodongIntro(text: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 145.dp), verticalAlignment = Alignment.Bottom) {
        Surface(Modifier.weight(1f).padding(bottom = 20.dp), color = Color(0xFFFFF4DF), shape = RoundedCornerShape(20.dp)) { Text(text, Modifier.padding(18.dp), color = Color(0xFF2E1E06), fontSize = 23.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold) }
        Image(painterResource(R.drawable.podong_wave), contentDescription = "뽀동이", modifier = Modifier.width(150.dp).height(130.dp))
    }
}
@Composable private fun CelebrationHeader(celebration: Celebration, reduceMotion: Boolean, onEvent: (FuelUiEvent) -> Unit) {
    var pose by remember(celebration.id) { mutableStateOf(celebrationPose(celebration, System.nanoTime() / 1_000_000L)) }
    LaunchedEffect(celebration.id, reduceMotion) {
        if (!reduceMotion) {
            while (true) {
                val next = celebrationPose(celebration, System.nanoTime() / 1_000_000L) ?: break
                pose = next
                delay(16)
            }
        }
        onEvent(FuelUiEvent.CelebrationFinished(celebration.id))
    }
    if (reduceMotion || pose == null) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        val message = when (celebration.variant) { Variant.Small -> "냠, 간식!"; Variant.Full -> "빵빵해요!"; Variant.Recovery -> "살았다…!"; else -> "든든하게 채웠어요" }
        Surface(Modifier.weight(1f).padding(bottom = 20.dp), shape = RoundedCornerShape(18.dp), color = Color(0xFFFFF4DF)) { Text(message, Modifier.padding(18.dp), color = Color(0xFF2E1E06), fontSize = 23.sp, fontWeight = FontWeight.Bold) }
        Box(Modifier.width(190.dp).height(150.dp).clip(RoundedCornerShape(4.dp)).semantics { contentDescription = "주유를 마치고 인사하는 뽀동이" }, contentAlignment = Alignment.BottomEnd) {
            val resource = when (pose) { Pose.Peek -> R.drawable.podong_peek; Pose.Wave -> R.drawable.podong_wave; Pose.Cheek -> R.drawable.podong_cheek; Pose.Wiggle -> R.drawable.podong_wiggle; Pose.Tuck -> R.drawable.podong_tuck; Pose.Sparkle, null -> null }
            var wiggle by remember(celebration.id, pose) { mutableStateOf(false) }
            LaunchedEffect(pose) { if (pose == Pose.Wiggle) repeat(4) { wiggle = !wiggle; delay(90) } }
            val rotation by animateFloatAsState(if (pose == Pose.Wiggle) { if (wiggle) 5f else -5f } else 0f, label = "podongWiggle")
            if (resource != null) Image(painterResource(resource), null, Modifier.width(190.dp).height(145.dp).graphicsLayer { rotationZ = rotation; translationX = if (pose == Pose.Peek) -28.dp.toPx() else 0f })
            else Text("✦", color = GoldText, fontSize = 36.sp, modifier = Modifier.padding(30.dp))
        }
    }
}
@Composable private fun Analysis(state: FuelUiState, route: FuelRouteUi, showCharacter: Boolean, narrow: Boolean, select: (FuelStation) -> Unit, onEvent: (FuelUiEvent) -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF100F0D)).verticalScroll(rememberScrollState())) {
        ScreenHeader(if (state.refuel != null) "주유 후 연료 확인" else "주유가 필요한 이유", onEvent)
        if (state.refuel != null) {
            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Copy("현재 연료로 갈 수 있는 거리를\n다시 확인해 주세요.", 28)
                Text("주행 가능 거리 ${km(state.refuel.rangeKm)}", color = GoldText, fontSize = 30.sp)
                Text("주유 전 분석은 재사용하지 않아요. 호스트 앱에서 최신 연료와 경로로 다시 판단해 주세요.", color = Muted, fontSize = 20.sp)
            }
        } else {
            val reason: @Composable () -> Unit = {
                val next = route.nextAfterPassing
                val target = if (state.motion != Motion.Parked && next != null) next.distanceKm else route.destinationKm
                val shortage = shortfall(route.rangeKm, target)
                Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    Copy(if (shortage != null) if (next != null && state.motion != Motion.Parked) "다음 주유소까지\n연료가 부족할 수 있어요" else "목적지까지 가려면\n중간 주유가 필요해요" else "현재 연료와 경로를\n확인해 주세요", 30)
                    Text("지금 연료로 갈 수 있는 거리\n${km(route.rangeKm)}", color = GoldText, fontSize = 24.sp)
                    Text("${if (next != null && state.motion != Motion.Parked) "다음 주유소까지" else "목적지까지"}\n${km(target)}", color = White, fontSize = 24.sp)
                    shortage?.let { Text("약 ${number(it)}km 부족해요.", color = Coral, fontSize = 24.sp) }
                    Text("차량의 주행 가능 거리와 경로 거리로 비교한 예상이에요. 실제 주행 상황에 따라 달라져요.", color = Muted, fontSize = 18.sp)
                    if (showCharacter) route.recommendationReason?.let { PodongIntro(it) }
                }
            }
            val stations: @Composable () -> Unit = {
                Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Copy("어디서 주유하면 좋을까요?", 25)
                    route.stations.forEach { s ->
                        HorizontalDivider(color = Line)
                        if (s.id == route.recommended?.id) Text("추천", color = GoldText, fontSize = 18.sp)
                        StationCopy(s)
                        valid(s.remainingAtStationKm)?.let { Text("이 주유소에 도착하면 약 ${number(it)}km를 더 갈 수 있어요.", color = Muted, fontSize = 18.sp) }
                        shortfall(route.rangeKm, s.distanceKm)?.let { Text("도달하기까지 약 ${number(it)}km 부족해요.", color = Coral, fontSize = 18.sp) }
                    }
                    route.recommended?.let { Primary("${it.name}에서 주유하기") { select(it) } }
                }
            }
            if (narrow) { reason(); stations() } else Row { Box(Modifier.weight(1f)) { reason() }; Box(Modifier.weight(1f).background(Graphite)) { stations() } }
        }
    }
}
@Composable private fun ScreenHeader(title: String, onEvent: (FuelUiEvent) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Copy(title, 25); TextButton(onClick = { onEvent(FuelUiEvent.Continue) }) { Text("지도 보기 ›", color = GoldText, fontSize = 19.sp) } }
    HorizontalDivider(color = Line)
}
@Composable private fun Settings(state: FuelUiState, onEvent: (FuelUiEvent) -> Unit) {
    Column(Modifier.fillMaxSize().background(Graphite).verticalScroll(rememberScrollState())) {
        ScreenHeader("주유 안내 설정", onEvent)
        Row(Modifier.fillMaxWidth().padding(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { Copy("뽀동이 인사", 25); Text("주유 완료 후 정차 중에만 짧게 인사해요.\n꺼도 주유 안내와 완료 확인은 유지돼요.", color = Muted, fontSize = 20.sp) }
            Switch(checked = state.characterEnabled, onCheckedChange = { onEvent(FuelUiEvent.CharacterChanged(it)) })
        }
        HorizontalDivider(color = Line)
        Text("주행 중에는 큰 캐릭터를 표시하지 않아요.\n캐릭터는 소리 없이 한 번만 인사해요.", Modifier.padding(32.dp), color = Muted, fontSize = 21.sp, lineHeight = 32.sp)
    }
}
