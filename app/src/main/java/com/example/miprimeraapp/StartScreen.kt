package com.example.miprimeraapp

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val OceanInk = Color(0xFF14366F)
private val CoverHeading = FontFamily(Font(R.font.bowlby_one_regular))

@OptIn(ExperimentalTextApi::class)
private val CoverBody = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    FontFamily(Font(
        R.font.ones_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ))
} else FontFamily.SansSerif

@Composable
fun StartScreen() {
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var showDemoNotice by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFF0FFFF), Color(0xFFDBFFFF))),
        ).navigationBarsPadding(),
    ) {
        val pageHeight = maxOf(maxHeight, 760.dp)
        val headingSize = if (maxWidth < 380.dp) 27.sp else 31.sp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(pageHeight * 0.52f)) {
                Image(
                    painterResource(R.drawable.onboardingscreen_cover),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxWidth().height(40.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFE5FFFF)))))
            }
            Column(
                Modifier.widthIn(max = 540.dp).fillMaxWidth().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(pageHeight * 0.035f))
                Text(
                    stringResource(R.string.start_title),
                    fontFamily = CoverHeading,
                    fontSize = headingSize,
                    lineHeight = headingSize,
                    letterSpacing = (-0.6).sp,
                    color = OceanInk,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.start_description),
                    fontFamily = CoverBody,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    color = OceanInk,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(pageHeight * 0.04f))
                Button(
                    onClick = { showDemoNotice = true },
                    modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(0.84f).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0085FF), contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp),
                ) {
                    Text(stringResource(R.string.start_demo), fontFamily = CoverBody,
                        fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_forward),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).align(Alignment.CenterVertically),
                    )
                }
                Spacer(Modifier.height(pageHeight * 0.055f))
                TextButton(onClick = { showInfo = true }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+", color = OceanInk, fontSize = 28.sp, lineHeight = 30.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.start_more), color = OceanInk,
                            fontSize = 11.sp, letterSpacing = 3.sp)
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    if (showInfo || showDemoNotice) {
        AlertDialog(
            onDismissRequest = { showInfo = false; showDemoNotice = false },
            title = { Text(stringResource(if (showInfo) R.string.start_info_title else R.string.start_demo)) },
            text = { Text(stringResource(if (showInfo) R.string.start_info_body else R.string.start_demo_pending)) },
            confirmButton = {
                TextButton(onClick = { showInfo = false; showDemoNotice = false }) {
                    Text(stringResource(R.string.start_close), color = OceanInk)
                }
            },
        )
    }
}