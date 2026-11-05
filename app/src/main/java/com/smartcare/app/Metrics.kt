package com.smartcare.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs

data class StorageInfo(val total: Long, val free: Long) {
    val used: Long get() = total - free
    val percent: Float get() = if (total > 0) used * 100f / total else 0f
}

data class RamInfo(val total: Long, val available: Long, val lowMemory: Boolean) {
    val used: Long get() = total - available
    val percent: Float get() = if (total > 0) used * 100f / total else 0f
}

data class BatteryInfo(
    val percent: Int,
    val tempC: Float,
    val voltageV: Float,
    val technology: String,
    val charging: Boolean,
    val status: String
)

data class Health(val score: Int, val reasons: List<String>)

data class Snapshot(
    val storage: StorageInfo,
    val ram: RamInfo,
    val battery: BatteryInfo,
    val health: Health,
    val timeMillis: Long
)

object Metrics {

    // Leitura pontual: so acontece quando o app abre ou o usuario toca em Atualizar.
    fun read(ctx: Context): Snapshot {
        val storage = readStorage()
        val ram = readRam(ctx)
        val battery = readBattery(ctx)
        return Snapshot(storage, ram, battery, computeHealth(storage, ram, battery), System.currentTimeMillis())
    }

    private fun readStorage(): StorageInfo {
        val st = StatFs(Environment.getDataDirectory().path)
        return StorageInfo(st.totalBytes, st.availableBytes)
    }

    private fun readRam(ctx: Context): RamInfo {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return RamInfo(mi.totalMem, mi.availMem, mi.lowMemory)
    }

    private fun readBattery(ctx: Context): BatteryInfo {
        val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val rawTemp = i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val tempC = if (rawTemp == Int.MIN_VALUE) Float.NaN else rawTemp / 10f
        val rawVolt = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tech = i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Indisponível"
        val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        val statusText = when (st) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Carregando"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Descarregando"
            BatteryManager.BATTERY_STATUS_FULL -> "Carga completa"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Não está carregando"
            else -> "Indisponível"
        }
        return BatteryInfo(percent, tempC, rawVolt / 1000f, tech, charging, statusText)
    }

    // Criterios transparentes. Nao e uma certificacao oficial do Android.
    private fun computeHealth(s: StorageInfo, r: RamInfo, b: BatteryInfo): Health {
        var score = 100
        val reasons = mutableListOf<String>()

        val sp = s.percent
        if (sp >= 95f) {
            score -= 30
            reasons.add("Armazenamento acima de 95% (−30)")
        } else if (sp >= 90f) {
            score -= 20
            reasons.add("Armazenamento acima de 90% (−20)")
        } else if (sp >= 80f) {
            score -= 10
            reasons.add("Armazenamento acima de 80% (−10)")
        } else if (sp >= 70f) {
            score -= 5
            reasons.add("Armazenamento acima de 70% (−5)")
        }

        if (r.lowMemory) {
            score -= 10
            reasons.add("O Android sinalizou pouca memória disponível (−10)")
        }

        if (!b.tempC.isNaN()) {
            if (b.tempC >= 45f) {
                score -= 20
                reasons.add("Bateria com temperatura de 45 °C ou mais (−20)")
            } else if (b.tempC >= 40f) {
                score -= 10
                reasons.add("Bateria com temperatura de 40 °C ou mais (−10)")
            }
        }

        if (reasons.isEmpty()) reasons.add("Nenhum ponto de atenção encontrado agora")
        return Health(score.coerceIn(0, 100), reasons)
    }
}
