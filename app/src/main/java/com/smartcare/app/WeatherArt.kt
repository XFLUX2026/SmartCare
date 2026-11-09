package com.smartcare.app

import android.graphics.*

object WeatherArt {
    enum class Kind { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

    fun kind(code: Int) = when (code) {
        0 -> Kind.CLEAR
        1, 2 -> Kind.PARTLY
        3 -> Kind.CLOUDY
        45, 48 -> Kind.FOG
        in 51..57 -> Kind.DRIZZLE
        in 61..67, in 80..82 -> Kind.RAIN
        in 71..77, 85, 86 -> Kind.SNOW
        in 95..99 -> Kind.STORM
        else -> Kind.CLOUDY
    }

    fun label(code: Int) = when (kind(code)) {
        Kind.CLEAR -> "Céu limpo"; Kind.PARTLY -> "Parcialmente nublado"
        Kind.CLOUDY -> "Nublado"; Kind.FOG -> "Neblina"; Kind.DRIZZLE -> "Garoa"
        Kind.RAIN -> "Chuva"; Kind.SNOW -> "Neve"; Kind.STORM -> "Tempestade"
    }

    /** Disco escuro + anel néon azul→roxo com brilho. */
    fun ring(s: Int): Bitmap {
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val m = s / 2f
        val r = s * 0.44f
        p.shader = RadialGradient(m, m, r, 0xF2162468.toInt(), 0xF2050A22.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(m, m, r, p)
        p.shader = SweepGradient(m, m, intArrayOf(
            0xFF00E5FF.toInt(), 0xFF2D5BFF.toInt(), 0xFFB14CFF.toInt(), 0xFF00E5FF.toInt()
        ), null)
        p.style = Paint.Style.STROKE
        val sw = s * 0.028f
        for ((k, a) in listOf(3.4f to 40, 2.1f to 90, 1f to 255)) {
            p.strokeWidth = sw * k
            p.alpha = a
            c.drawCircle(m, m, r, p)
        }
        p.strokeWidth = sw * 0.35f
        p.alpha = 120
        c.drawCircle(m, m, r * 0.9f, p)
        return b
    }

    fun icon(k: Kind, day: Boolean, s: Int): Bitmap {
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val u = s / 100f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        fun sun(cx: Float, cy: Float, r: Float) {
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.color = 0xFFFFB300.toInt()
            p.strokeWidth = r * 0.22f
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0)
                val x = Math.cos(a).toFloat()
                val y = Math.sin(a).toFloat()
                c.drawLine(cx + x * r * 1.3f, cy + y * r * 1.3f, cx + x * r * 1.65f, cy + y * r * 1.65f, p)
            }
            p.style = Paint.Style.FILL
            p.shader = RadialGradient(cx, cy, r, 0xFFFFE87A.toInt(), 0xFFFF9800.toInt(), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)
            p.shader = null
        }

        fun moon(cx: Float, cy: Float, r: Float) {
            val path = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
            path.op(
                Path().apply { addCircle(cx + r * 0.5f, cy - r * 0.3f, r * 0.85f, Path.Direction.CW) },
                Path.Op.DIFFERENCE
            )
            p.style = Paint.Style.FILL
            p.shader = null
            p.color = 0xFFFFF1A8.toInt()
            c.drawPath(path, p)
        }

        fun cloud(cx: Float, cy: Float, w: Float, top: Int, bottom: Int) {
            p.style = Paint.Style.FILL
            p.shader = LinearGradient(0f, cy - w * 0.3f, 0f, cy + w * 0.2f, top, bottom, Shader.TileMode.CLAMP)
            c.drawCircle(cx - w * 0.22f, cy + w * 0.02f, w * 0.18f, p)
            c.drawCircle(cx + w * 0.02f, cy - w * 0.08f, w * 0.26f, p)
            c.drawCircle(cx + w * 0.26f, cy + w * 0.04f, w * 0.19f, p)
            c.drawRoundRect(cx - w * 0.4f, cy + w * 0.02f, cx + w * 0.45f, cy + w * 0.22f, w * 0.1f, w * 0.1f, p)
            p.shader = null
        }

        fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, w: Float) {
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.color = color
            p.strokeWidth = w
            c.drawLine(x1, y1, x2, y2, p)
        }

        val white = 0xFFFFFFFF.toInt()
        val blue = 0xFFC7D7F5.toInt()
        val grayTop = 0xFFB9C4DC.toInt()
        val grayBot = 0xFF7F8CA8.toInt()
        when (k) {
            Kind.CLEAR -> if (day) sun(50 * u, 50 * u, 21 * u) else moon(50 * u, 50 * u, 24 * u)
            Kind.PARTLY -> {
                if (day) sun(36 * u, 34 * u, 15 * u) else moon(36 * u, 34 * u, 15 * u)
                cloud(56 * u, 58 * u, 64 * u, white, blue)
            }
            Kind.CLOUDY -> {
                cloud(62 * u, 38 * u, 44 * u, grayTop, grayBot)
                cloud(46 * u, 56 * u, 70 * u, white, blue)
            }
            Kind.FOG -> {
                cloud(50 * u, 38 * u, 60 * u, white, blue)
                for (i in 0..2) line(22 * u, (62 + i * 11) * u, 78 * u, (62 + i * 11) * u, 0xFFB8C6E0.toInt(), 5 * u)
            }
            Kind.DRIZZLE, Kind.RAIN -> {
                cloud(50 * u, 38 * u, 66 * u, grayTop, grayBot)
                val len = if (k == Kind.RAIN) 16 else 9
                for (i in 0..2) line((34 + i * 16) * u, 66 * u, (31 + i * 16) * u, (66 + len) * u, 0xFF4DA3FF.toInt(), 5 * u)
            }
            Kind.SNOW -> {
                cloud(50 * u, 38 * u, 66 * u, white, blue)
                p.shader = null
                p.style = Paint.Style.FILL
                p.color = 0xFFBFE3FF.toInt()
                for (i in 0..2) c.drawCircle((34 + i * 16) * u, (74 + (i % 2) * 8) * u, 4.5f * u, p)
            }
            Kind.STORM -> {
                cloud(50 * u, 36 * u, 68 * u, 0xFF8794B0.toInt(), 0xFF4B5675.toInt())
                val bolt = Path().apply {
                    moveTo(54 * u, 52 * u); lineTo(40 * u, 74 * u); lineTo(50 * u, 74 * u)
                    lineTo(44 * u, 92 * u); lineTo(66 * u, 66 * u); lineTo(55 * u, 66 * u); close()
                }
                p.shader = null
                p.style = Paint.Style.FILL
                p.color = 0xFFFFC107.toInt()
                c.drawPath(bolt, p)
            }
        }
        return b
    }
}
