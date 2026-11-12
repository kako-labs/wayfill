package dev.fuelpilot.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fuelpilot.feature.assist.AssistIntent
import dev.fuelpilot.feature.assist.AssistViewState
import java.util.Locale

@Composable internal fun SunsetStatusScreen(state:AssistViewState,model:AdaptiveUiModel,onIntent:(AssistIntent)->Unit) {
    val ui=state.ui
    val refuel=model.status==AdaptiveStatus.Refuel
    val pending=model.status==AdaptiveStatus.Pending
    val error=model.status==AdaptiveStatus.Error
    val tag=when{refuel->"refuel_success";pending->"navigation_pending";error->"navigation_error";else->"navigation_success"}
    val title=when {
        refuel->when(ui.refuel?.let(::refuelVariant)){Variant.Small->"조금 더 가볍게 출발해요";Variant.Recovery->"다시 달릴 힘을 채웠어요";else->"든든하게 채웠어요"}
        pending->"내비게이션에 전달했어요"
        error->"안내를 완료하지 못했어요"
        else->"주유소 경로가 준비됐어요"
    }
    val detail=when {
        refuel->"새 연료 상태로 다시 살펴볼게요."
        pending->"경로가 바뀌었는지 응답을 기다리고 있어요."
        error->ui.error?:"현재 추천을 확인한 뒤 다시 시도해 주세요."
        else->"차량 내비게이션의 경로를 따라가세요."
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp).testTag(tag),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
        val active=ui.celebration?.takeIf{refuel&&characterPresentation(ui).playful}
        if(active!=null) PodongSprite(Modifier.size(250.dp),clipName(active.variant),active.startedAtMs,celebrationDuration(active.variant)) {
            onIntent(AssistIntent.CelebrationFinished(active.id))
        } else PodongPortrait(Modifier.size(if(refuel)250.dp else 190.dp))
        Text(title,color=FuelPilotColors.Text,fontSize=36.sp,lineHeight=46.sp,fontWeight=FontWeight.Bold)
        Text(detail,color=FuelPilotColors.Muted,fontSize=22.sp,lineHeight=31.sp)
        if(refuel) Surface(Modifier.widthIn(max=760.dp).fillMaxWidth(),color=FuelPilotColors.Surface,
            shape=RoundedCornerShape(26.dp),border=BorderStroke(1.dp,FuelPilotColors.Line)) {
            Row(Modifier.background(FuelPilotColors.Glass).padding(24.dp),horizontalArrangement=Arrangement.spacedBy(32.dp)) {
                for((label,value,unit) in listOf(Triple("확인된 주유량",ui.refuel?.liters," L"),Triple("확인 후 주행 가능",ui.refuel?.rangeKm," km"))) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(label,color=FuelPilotColors.Muted,fontSize=20.sp)
                        val clean=value?.takeIf{it.isFinite()&&it>=0}
                        Text(clean?.let{(if(it%1.0==0.0)it.toLong().toString()else String.format(Locale.ROOT,"%.1f",it))+unit}?:"미수신",
                            color=FuelPilotColors.Text,fontSize=34.sp,fontWeight=FontWeight.Bold)
                    }
                }
            }
        }
        if(pending) LinearProgressIndicator(Modifier.widthIn(max=600.dp).fillMaxWidth().padding(vertical=24.dp),color=FuelPilotColors.Gold,trackColor=FuelPilotColors.Line)
        else Column(Modifier.widthIn(max=600.dp).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(error&&model.actionableStationId!=null&&model.actionableRecommendationKey!=null) Button(
                onClick={onIntent(AssistIntent.SelectStation(model.actionableStationId,model.actionableRecommendationKey))},
                modifier=Modifier.fillMaxWidth().heightIn(min=76.dp).testTag("status_retry"),shape=RoundedCornerShape(99.dp)) { Text("다시 안내",fontSize=24.sp) }
            Button(onClick={onIntent(AssistIntent.Continue)},modifier=Modifier.fillMaxWidth().heightIn(min=76.dp).testTag("status_continue"),shape=RoundedCornerShape(99.dp)) {
                Text(if(error)"홈으로" else "확인했어요",fontSize=24.sp,fontWeight=FontWeight.Bold)
            }
        }
    }
}
