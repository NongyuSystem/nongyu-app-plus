package com.nongyu.appplus.ui.timetable

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * 水平仪倾斜角（票 #60 需求 3.1）：流水水面按手机姿态倾斜。
 *
 * ## 为什么必须低通滤波
 *
 * 蓝叠的加速度计 **maxRate 只有 5Hz**（建票时实测，见票 #60 评论），
 * 直接把原始值喂给动画会「一跳一跳」。这里用一阶低通：
 *
 *     smooth = smooth + (raw - smooth) * alpha
 *
 * alpha 取 0.15，约 6~7 帧追平，视觉上是「缓慢跟随」而不是「抽动」。
 *
 * ## 为什么用 DisposableEffect
 *
 * 传感器注册后必须反注册，否则页面切走后仍在持续采样、耗电。
 * `DisposableEffect` 的 onDispose 正好覆盖「离开这个 Composable」的时机。
 *
 * ## 返回什么
 *
 * 返回**横向倾斜角（度）**，约定：
 * - 0 = 手机水平放正（水面不歪）；
 * - 正值 = 手机左侧抬高，水往右流；
 * - 负值 = 手机右侧抬高，水往左流。
 *
 * 只取重力在屏幕 X 轴上的分量算倾角，不关心 Y 轴（前后倾）——
 * 时间条是竖直一条，前后倾对它的视觉效果没有意义。
 */
@Composable
fun rememberWaterTilt(maxDegrees: Float = 8f): Float {
    val context = LocalContext.current
    var tilt by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sm == null || accel == null) {
            // 没有传感器：保持静止，不抛异常（模拟器/部分设备可能没有）
            return@DisposableEffect onDispose { }
        }

        var smooth = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // event.values[0] = 屏幕 X 轴上的重力分量（m/s^2）
                val gx = event.values[0]
                val gy = event.values[1]
                // 与水平面的夹角；gy 作为参考量，接近 0 时避免除零
                val rawDeg = Math.toDegrees(
                    kotlin.math.atan2(gx.toDouble(), kotlin.math.abs(gy.toDouble()).coerceAtLeast(1.0)),
                ).toFloat()
                val clamped = rawDeg.coerceIn(-maxDegrees, maxDegrees)
                // 一阶低通，5Hz 输入下避免一跳一跳
                smooth += (clamped - smooth) * 0.15f
                tilt = smooth
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)

        onDispose {
            sm.unregisterListener(listener)
        }
    }

    return tilt
}
