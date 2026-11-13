package dev.fuelpilot.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fuelpilot.feature.assist.AssistIntent
import dev.fuelpilot.feature.assist.AssistSignal
import dev.fuelpilot.feature.assist.AssistViewState
import dev.fuelpilot.feature.assist.labels
import dev.fuelpilot.feature.assist.onlyStationsMissing
import java.util.Locale

object FuelPilotColors {
    val Background = Color(0xFF34272D)
    val Surface = Color(0xFF624A43)
    val Line = Color(0xFFCAA58B)
    val Text = Color(0xFFFFF3DB)
    val Muted = Color(0xFFECD8CA)
    val Gold = Color(0xFFFFDA91)
    val Backdrop = Brush.linearGradient(listOf(Color(0xFF9B746A),Color(0xFF604445),Background))
    val Glass = Brush.linearGradient(listOf(Color(0xB3926D59),Color(0xCC604640)))
}

@Composable fun FuelPilotTheme(content:@Composable ()->Unit) {
    MaterialTheme(colorScheme=darkColorScheme(primary=FuelPilotColors.Gold,onPrimary=Color(0xFF563414),
        secondary=FuelPilotColors.Gold,surface=FuelPilotColors.Surface,onSurface=FuelPilotColors.Text,
        background=FuelPilotColors.Background,onBackground=FuelPilotColors.Text),content=content)
}

/** Approved road-ribbon Podong, shared byte-for-byte with the web. */
@Composable fun PodongPortrait(modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalPodongStill provides true) { PodongSprite(modifier) }
}

private val KoreanBoundary = Regex("(?<=[가-힣])(?=[가-힣])")
@Composable private fun Label(value:String,size:Int,modifier:Modifier=Modifier,color:Color=FuelPilotColors.Text,bold:Boolean=false,lines:Int=2) {
    Text(value.replace(KoreanBoundary,"\u2060"),modifier.semantics { text=AnnotatedString(value) },
        color=color,fontSize=size.sp,lineHeight=(size*1.3f).sp,fontWeight=if(bold) FontWeight.Bold else FontWeight.Normal,
        maxLines=lines,overflow=TextOverflow.Ellipsis)
}

@Composable internal fun BoxScope.RedesignedCompanion(
    state:FuelUiState,route:FuelRouteUi,model:AdaptiveUiModel,wide:Boolean,compact:Boolean,
    shortWindow:Boolean,passing:Boolean,onEvent:(AssistIntent)->Unit,
) {
    val copy=fuelRecommendationCopy(route,passing)
    val recommendation=model.status==AdaptiveStatus.Recommendation && state.motion!=Motion.Unknown
    val actionable=state.motion!=Motion.Unknown && model.actionableStationId!=null && model.actionableRecommendationKey!=null
    val shell=if(compact) Modifier.fillMaxSize() else Modifier.align(Alignment.BottomStart).padding(24.dp).widthIn(max=760.dp).fillMaxWidth()
    Surface(shell.testTag(when(state.motion){Motion.Moving->"moving_companion";Motion.Parked->"parked_companion";else->"unknown_companion"}),
        color=Color.Transparent,shape=RoundedCornerShape(if(compact) 0.dp else 24.dp),border=if(compact) null else BorderStroke(1.dp,FuelPilotColors.Line)) {
        BoxWithConstraints(Modifier.background(FuelPilotColors.Backdrop)) {
            SunsetBackdrop(Modifier.matchParentSize(),dimmed=true)
            val dense=maxHeight<680.dp
            Column(Modifier.fillMaxWidth().then(if(compact) Modifier.fillMaxHeight() else Modifier).padding(if(dense) 18.dp else 24.dp),
                verticalArrangement=Arrangement.spacedBy(if(dense) 8.dp else 12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Label("WAYFILL",16,Modifier.weight(1f),bold=true,lines=1)
                    Label(when(state.motion){Motion.Moving->"주행 중";Motion.Parked->"정차 중";else->"차량 확인 중"},14,color=FuelPilotColors.Gold,lines=1)
                }
                if(recommendation) {
                    if(!shortWindow && !dense) Label(if(passing) "지나치기 전에 확인해요" else "이번 경로의 주유 추천",16,color=FuelPilotColors.Gold,lines=1)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Label(if(passing) "앞 주유소에서 주유하고 가세요" else "목적지 전에 한 번 주유해요",if(wide)32 else if(dense)24 else 26,Modifier.weight(1f),bold=true,lines=2)
                        PodongPortrait(Modifier.size(if(shortWindow)52.dp else if(dense)64.dp else 88.dp).testTag("podong_calm"))
                    }
                    val station=route.recommended
                    val distance=station?.distanceKm?.takeIf { it.isFinite() && it>=0 }?.let {
                        if(it%1.0==0.0) it.toLong().toString() else String.format(Locale.ROOT,"%.1f",it)
                    }
                    if(shortWindow) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp),verticalAlignment=Alignment.CenterVertically) {
                        Label(distance?.let{"$it km 앞"}?:"거리 확인 중",32,Modifier.weight(.8f),bold=true,lines=1)
                        Column(Modifier.weight(1.2f)) { Label(copy.station,22,bold=true,lines=1); Label(copy.routeFacts,18,color=FuelPilotColors.Muted,lines=1) }
                    } else {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom) {
                            Label(distance?:"—",if(dense)40 else 56,bold=true,lines=1)
                            Label(if(distance==null)"거리 확인 중" else " km 앞",20,Modifier.weight(1f).padding(bottom=5.dp),color=FuelPilotColors.Muted,lines=1)
                            station?.extraMinutes?.takeIf { it>=0 }?.let { Label("경로 +${it}분",18,Modifier.padding(bottom=5.dp),color=FuelPilotColors.Gold,lines=1) }
                        }
                        Surface(color=Color.Transparent,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,FuelPilotColors.Line)) {
                            Column(Modifier.background(FuelPilotColors.Glass).fillMaxWidth().padding(if(dense)14.dp else 18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Label(copy.station,24,bold=true,lines=2)
                                Label(copy.reason,if(dense)17 else 18,color=FuelPilotColors.Muted,lines=if(dense)1 else 2)
                            }
                        }
                    }
                } else {
                    val title=when {
                        state.motion==Motion.Unknown->"차량 연결을 기다리고 있어요"
                        model.status==AdaptiveStatus.Loading->"주유 안내를 준비하고 있어요"
                        model.status==AdaptiveStatus.Stale->"최신 정보를 확인하고 있어요"
                        model.blockedBy.onlyStationsMissing() && model.status in setOf(AdaptiveStatus.Unsupported,AdaptiveStatus.Unavailable)->"주유소 정보를 기다리고 있어요"
                        model.status==AdaptiveStatus.Unsupported->if(model.blockedBy.isEmpty()) "차량 연결이 필요해요" else "${model.blockedBy.labels()} 연결이 필요해요"
                        model.status==AdaptiveStatus.Unavailable->if(model.blockedBy.isEmpty()) "차량 정보를 기다리고 있어요" else "${model.blockedBy.labels()} 정보를 기다리고 있어요"
                        model.status==AdaptiveStatus.Pending->"내비게이션에 전달했어요"
                        model.status==AdaptiveStatus.Success->"주유소 경로가 준비됐어요"
                        model.status==AdaptiveStatus.Refuel->"주유 완료를 확인했어요"
                        model.status==AdaptiveStatus.Error->"안내를 완료하지 못했어요"
                        else->"필요한 순간에 알려드릴게요"
                    }
                    val detail=when(model.status){
                        AdaptiveStatus.Pending->"경로가 바뀌었는지 응답을 기다리고 있어요."
                        AdaptiveStatus.Success->"차량 내비게이션의 경로를 따라가세요."
                        AdaptiveStatus.Error->state.error?:"현재 추천을 확인한 뒤 다시 시도해 주세요."
                        else->"신호가 확인되면 현재 경로에 맞춰 안내할게요."
                    }
                    Box(Modifier.fillMaxWidth().height(if(shortWindow)48.dp else if(dense)108.dp else 180.dp),contentAlignment=Alignment.Center) {
                        PodongPortrait(Modifier.size(if(shortWindow)48.dp else if(dense)108.dp else 152.dp).testTag("podong_calm"))
                    }
                    Label(title,if(shortWindow)24 else 28,bold=true,lines=2)
                    Label(detail,20,color=FuelPilotColors.Muted,lines=2)
                    if(!shortWindow && (state.motion==Motion.Unknown || model.status in setOf(AdaptiveStatus.Loading,AdaptiveStatus.Stale,AdaptiveStatus.Unsupported,AdaptiveStatus.Unavailable))) {
                        Surface(color=Color.Transparent,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,FuelPilotColors.Line)) {
                            Column(Modifier.background(FuelPilotColors.Glass).fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                // Name what was actually confirmed; with no known cause every row stays "확인 대기".
                                val known=state.motion!=Motion.Unknown && model.blockedBy.isNotEmpty()
                                for(signal in listOf(AssistSignal.VEHICLE_ENERGY,AssistSignal.ROUTE,AssistSignal.STATIONS)) Row(Modifier.fillMaxWidth().testTag("signal_${signal.name.lowercase()}")) {
                                    val confirmed=known && signal !in model.blockedBy
                                    Label(signal.label,18,Modifier.weight(1f),color=FuelPilotColors.Muted,lines=1)
                                    Label(if(confirmed) "확인됨" else if(known && signal==AssistSignal.STATIONS) "연결 필요" else "확인 대기",16,color=if(confirmed) FuelPilotColors.Muted else FuelPilotColors.Gold,lines=1)
                                }
                            }
                        }
                    }
                }
                if(compact && !shortWindow) Spacer(Modifier.weight(1f))
                if(actionable) {
                    val guide={onEvent(AssistIntent.SelectStation(model.actionableStationId!!,model.actionableRecommendationKey!!))}
                    val later={onEvent(AssistIntent.Continue)}
                    if(shortWindow || !compact) Row(Modifier.fillMaxWidth().testTag("moving_recommendation_actions"),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        CompanionButton("바로 안내","guide_now",true,Modifier.weight(1f),guide); CompanionButton("나중에","later",false,Modifier.weight(1f),later)
                    } else Column(Modifier.fillMaxWidth().testTag("moving_recommendation_actions"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        CompanionButton("바로 안내","guide_now",true,Modifier.fillMaxWidth(),guide); CompanionButton("나중에","later",false,Modifier.fillMaxWidth(),later)
                    }
                }
            }
        }
    }
}

@Composable private fun CompanionButton(label:String,tag:String,primary:Boolean,modifier:Modifier,onClick:()->Unit) {
    val shape=RoundedCornerShape(99.dp)
    val brush=if(primary) Brush.linearGradient(listOf(Color(0xFFFFE9A3),Color(0xFFFFC566))) else FuelPilotColors.Glass
    Button(onClick,modifier.heightIn(min=76.dp).background(brush,shape).testTag(tag),shape=shape,
        colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent,contentColor=if(primary)Color(0xFF563414)else FuelPilotColors.Text),
        border=BorderStroke(1.dp,if(primary)Color(0xFFF5D99D)else FuelPilotColors.Line)) { Text(label,fontSize=24.sp,fontWeight=FontWeight.Bold) }
}

/** Decorative scenic artwork only; route geometry remains in the vehicle navigation app. */
@Composable fun SunsetBackdrop(modifier:Modifier=Modifier,dimmed:Boolean=false) {
    Box(modifier) {
        Image(painterResource(R.drawable.sunset_forecourt),null,Modifier.matchParentSize(),contentScale=ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
            Color(0x33422B32),Color(0x223C2727),Color(0xD934272D)))))
        if(dimmed) Box(Modifier.matchParentSize().background(Color(0x70402D32)))
    }
}

/** Approved sunset composition: open hero scene, then a single recommendation/action panel. */
@Composable fun FuelHomeOverview(state:AssistViewState,onIntent:(AssistIntent)->Unit,actionPrefix:String="home") {
    val model=adaptiveUiModel(state)
    val route=state.presentation.route
    val passing=state.presentation.targetScreen==Screen.Passing
    val copy=fuelRecommendationCopy(route,passing)
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("home_state_ready")) {
        val roomy=maxWidth>=1100.dp
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1.8f),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    Label(if(passing)"앞 주유소에서 주유하고 가요" else "가는 길에 주유하고 가요",if(roomy)48 else 36,bold=true,lines=2)
                    Label(copy.reason,if(roomy)26 else 22,color=FuelPilotColors.Muted,lines=2)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                        for((name,value) in listOf("주행 가능" to route.rangeKm,"목적지까지" to route.destinationKm)) {
                            Surface(Modifier.weight(1f),color=Color.Transparent,shape=RoundedCornerShape(24.dp),border=BorderStroke(1.dp,FuelPilotColors.Line)) {
                                Column(Modifier.background(FuelPilotColors.Glass).padding(20.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                    Label(name,20,color=FuelPilotColors.Muted,lines=1)
                                    Label(value?.takeIf{it.isFinite()&&it>=0}?.let{"${it.toInt()} km"}?:"확인 중",if(roomy)42 else 34,bold=true,lines=1)
                                }
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f).height(if(roomy)330.dp else 260.dp).padding(start=24.dp)) {
                    key(state.presentation.recommendationKey) {
                        CompositionLocalProvider(LocalPodongStill provides !characterPresentation(state.ui).playful) {
                            PodongSprite(Modifier.fillMaxSize(),name=if(state.recommendationStartedAtMs!=null)"recommend" else "calm",
                                startedAtMs=state.recommendationStartedAtMs)
                        }
                    }
                }
            }
            Surface(color=Color.Transparent,shape=RoundedCornerShape(30.dp),border=BorderStroke(1.dp,FuelPilotColors.Line)) {
                Row(Modifier.background(FuelPilotColors.Glass).fillMaxWidth().padding(if(roomy)30.dp else 24.dp),
                    horizontalArrangement=Arrangement.spacedBy(30.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1.25f),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        Label("이번 경로의 추천",26,bold=true,lines=1)
                        Label(copy.station,if(roomy)36 else 30,bold=true,lines=3)
                        Label(copy.routeFacts,24,color=FuelPilotColors.Muted,lines=2)
                        // Ranking explanation comes from the host; never fabricate reachability.
                        Label(route.recommendationReason?:"현재 경로에서 확인한 주유 기회예요.",20,color=FuelPilotColors.Gold,lines=2)
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(model.actionableStationId!=null && model.actionableRecommendationKey!=null) {
                            CompanionButton("바로 안내","${actionPrefix}_guide_now",true,Modifier.fillMaxWidth()) {
                                onIntent(AssistIntent.SelectStation(model.actionableStationId,model.actionableRecommendationKey))
                            }
                            CompanionButton("나중에","${actionPrefix}_later",false,Modifier.fillMaxWidth()) { onIntent(AssistIntent.Continue) }
                        }
                        Label("설정한 여유거리 ${state.persisted.minimumReserveKm} km",18,Modifier.align(Alignment.CenterHorizontally),color=FuelPilotColors.Muted,lines=1)
                    }
                }
            }
        }
    }
}
