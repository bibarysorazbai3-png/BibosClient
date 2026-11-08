package com.example.autotap

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var cpsInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val prefs = getSharedPreferences(ClickerService.PREFS, MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 2, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "AutoTap"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
        })

        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, pad / 2, 0, pad / 2)
        }
        root.addView(status)

        root.addView(TextView(this).apply {
            text = "Скорость (кликов в секунду, ${ClickerService.MIN_CPS}–${ClickerService.MAX_CPS}):"
            textSize = 15f
        })

        cpsInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt(ClickerService.KEY_CPS, 10).toString())
        }
        root.addView(cpsInput)

        root.addView(Button(this).apply {
            text = "Применить КПС"
            setOnClickListener {
                val v = cpsInput.text.toString().toIntOrNull()
                    ?.coerceIn(ClickerService.MIN_CPS, ClickerService.MAX_CPS)
                if (v == null) {
                    Toast.makeText(this@MainActivity, "Введите число", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.edit().putInt(ClickerService.KEY_CPS, v).apply()
                    cpsInput.setText(v.toString())
                    Toast.makeText(this@MainActivity, "Установлено: $v КПС", Toast.LENGTH_SHORT).show()
                }
            }
        })

        root.addView(TextView(this).apply {
            text = "\n1. Включите AutoTap в «Специальных возможностях».\n\n" +
                "2. Появятся красный прицел и панель: ⠿ — двигать панель, − / + — КПС " +
                "(удерживайте для быстрой смены), ▶ — старт/стоп, 👁 — скрыть/показать.\n\n" +
                "3. Перетащите прицел на нужную кнопку в игре и нажмите ▶."
            textSize = 15f
        })

        root.addView(Button(this).apply {
            text = "Открыть настройки"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        status.text = if (ClickerService.instance != null)
            "Сервис включён ✅" else "Сервис выключен ❌"
    }
}
