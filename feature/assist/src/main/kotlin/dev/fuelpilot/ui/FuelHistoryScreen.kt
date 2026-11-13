package dev.fuelpilot.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fuelpilot.feature.assist.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun number(value:Double)=if(value%1.0==0.0)value.toLong().toString()else String.format(Locale.ROOT,"%.1f",value)
private fun date(at:Long)=DateTimeFormatter.ofPattern("M월 d일 · HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at))

/** Verified durable records only; no inferred station, price, or event timestamp. */
@Composable fun FuelHistoryScreen(state:AssistViewState,onIntent:(AssistIntent)->Unit) {
    var selected by rememberSaveable{mutableStateOf<String?>(null)}
    var confirm by rememberSaveable{mutableStateOf(false)}
    var deleting by rememberSaveable{mutableStateOf(false)}
    val records=state.persisted.refuelHistory
    val detail=records.firstOrNull{it.id==selected}
    LaunchedEffect(records.isEmpty(),state.ready){if(records.isEmpty()||!state.ready)deleting=false}
    Column(Modifier.fillMaxSize().padding(horizontal=24.dp).testTag("history_screen")) {
        Row(Modifier.fillMaxWidth().padding(vertical=18.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if(detail==null)"주유 기록" else "주유 기록 상세",color=FuelPilotColors.Text,fontSize=32.sp,fontWeight=FontWeight.Bold)
                Text("이 기기에서 확인한 주유를 모았어요",color=FuelPilotColors.Muted,fontSize=18.sp)
            }
            if(detail!=null) TextButton(onClick={selected=null},modifier=Modifier.heightIn(min=76.dp).testTag("history_back")) { Text("목록으로",color=FuelPilotColors.Gold,fontSize=20.sp) }
            else if(records.isNotEmpty()) TextButton(onClick={confirm=true},enabled=state.ready&&!deleting,modifier=Modifier.heightIn(min=76.dp).testTag("history_delete")) {
                Text(if(deleting)"삭제 중…" else "기록 삭제",color=FuelPilotColors.Gold,fontSize=20.sp)
            }
        }
        if(!state.ready) Text("기록 저장 상태를 확인할 수 없어요. 앱을 다시 실행해 주세요.",color=FuelPilotColors.Gold,fontSize=20.sp)
        if(records.isEmpty()) Column(Modifier.weight(1f).fillMaxWidth().testTag("history_empty"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            PodongPortrait(Modifier.size(160.dp))
            Text("아직 확인한 주유가 없어요",color=FuelPilotColors.Text,fontSize=28.sp,fontWeight=FontWeight.Bold)
            Text("차량에서 주유가 확인되면 여기에 남겨요.",Modifier.padding(top=12.dp),color=FuelPilotColors.Muted,fontSize=20.sp)
        } else if(detail!=null) LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(bottom=24.dp)) {
            item{HistoryCard(detail,false,{})}
            item{HistoryInfo("앱에서 확인한 시각",date(detail.observedAtMs))}
            item{HistoryInfo("확인된 주유량",detail.liters?.let{"${number(it)} L"}?:"차량에서 받지 못했어요")}
            item{HistoryInfo("주유 확인 후 주행 가능 거리",detail.rangeKm?.let{"${number(it)} km"}?:"차량에서 받지 못했어요")}
            item{Text("주유소 이름·결제 금액·연비는 이 기록으로 확인할 수 없어요.",color=FuelPilotColors.Muted,fontSize=18.sp)}
        } else {
            Text("최근 90일 · ${records.size}건",Modifier.padding(bottom=14.dp),color=FuelPilotColors.Gold,fontSize=20.sp)
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=24.dp)) {
                items(records,key={it.id}){record->HistoryCard(record,true,{selected=record.id})}
                item{Text("최대 100건을 이 기기에 보관해요. 시각은 앱에서 주유 신호를 확인한 시각이에요.",color=FuelPilotColors.Muted,fontSize=17.sp)}
            }
        }
    }
    if(confirm&&state.ui.motion==Motion.Parked) AlertDialog(onDismissRequest={confirm=false},containerColor=FuelPilotColors.Surface,
        title={Text("주유 기록을 모두 삭제할까요?",color=FuelPilotColors.Text)},
        text={Text("주유량·거리 기록은 복구할 수 없어요. 같은 안내가 반복되지 않도록 최소 식별정보는 최대 90일 유지해요.",color=FuelPilotColors.Muted)},
        confirmButton={TextButton(onClick={confirm=false;deleting=true;selected=null;onIntent(AssistIntent.ClearRefuelHistory)},modifier=Modifier.heightIn(min=76.dp).testTag("history_confirm_delete")){Text("모두 삭제",color=FuelPilotColors.Gold,fontSize=20.sp)}},
        dismissButton={TextButton(onClick={confirm=false},modifier=Modifier.heightIn(min=76.dp).testTag("history_cancel_delete")){Text("취소",color=FuelPilotColors.Text,fontSize=20.sp)}})
}

@Composable private fun HistoryInfo(label:String,value:String) {
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Text(label,color=FuelPilotColors.Muted,fontSize=18.sp)
        Text(value,color=FuelPilotColors.Text,fontSize=26.sp,fontWeight=FontWeight.SemiBold)
        HorizontalDivider(Modifier.padding(top=12.dp),color=FuelPilotColors.Line.copy(alpha=.4f))
    }
}

@Composable private fun HistoryCard(record:RefuelRecord,clickable:Boolean,onClick:()->Unit) {
    val content:@Composable ()->Unit={Row(Modifier.fillMaxWidth().background(FuelPilotColors.Glass).padding(22.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(date(record.observedAtMs),color=FuelPilotColors.Muted,fontSize=18.sp)
            Text(if(record.isFull)"가득 주유를 확인했어요" else "주유를 확인했어요",color=FuelPilotColors.Text,fontSize=26.sp,fontWeight=FontWeight.Bold)
            Text(record.rangeKm?.let{"확인 후 주행 가능 ${number(it)} km"}?:"주행 가능 거리 미수신",color=FuelPilotColors.Muted,fontSize=18.sp)
        }
        Text(record.liters?.let{"${number(it)} L"}?:"주유량 미수신",color=FuelPilotColors.Gold,fontSize=24.sp,fontWeight=FontWeight.Bold)
    }}
    if(clickable) Surface(onClick=onClick,modifier=Modifier.fillMaxWidth().heightIn(min=100.dp).testTag("history_record"),color=FuelPilotColors.Surface,shape=RoundedCornerShape(24.dp),border=BorderStroke(1.dp,FuelPilotColors.Line),content=content)
    else Surface(color=FuelPilotColors.Surface,shape=RoundedCornerShape(24.dp),border=BorderStroke(1.dp,FuelPilotColors.Line),content=content)
}
