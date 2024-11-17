package com.bpareja.pomodorotec.pomodoro

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.CountDownTimer
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.bpareja.pomodorotec.MainActivity
import com.bpareja.pomodorotec.R
import kotlin.random.Random

enum class Phase {
    FOCUS, BREAK
}

class PomodoroViewModel(application: Application) : AndroidViewModel(application) {
    init {
        instance = this
    }

    companion object {
        private var instance: PomodoroViewModel? = null
        fun skipBreak() {
            instance?.startFocusSession()  // Saltar el descanso y comenzar sesión de concentración
        }
    }

    private val context = getApplication<Application>().applicationContext

    private val _timeLeft = MutableLiveData("25:00")
    val timeLeft: LiveData<String> = _timeLeft

    private val _isRunning = MutableLiveData(false)
    val isRunning: LiveData<Boolean> = _isRunning

    private val _currentPhase = MutableLiveData(Phase.FOCUS)
    val currentPhase: LiveData<Phase> = _currentPhase

    private val _isSkipBreakButtonVisible = MutableLiveData(false)
    val isSkipBreakButtonVisible: LiveData<Boolean> = _isSkipBreakButtonVisible

    private var countDownTimer: CountDownTimer? = null
    private var timeRemainingInMillis: Long = 25 * 60 * 1000L // Tiempo inicial para FOCUS

    // Mensajes motivacionales para las fases de concentración y descanso
    private val focusMessages = arrayOf(
        "¡Vamos, solo 25 minutos para lograr tus metas!",
        "¡Es hora de concentrarse, el éxito te espera!",
        "El enfoque es el camino al éxito. ¡A por ello!",
        "No te detengas ahora, cada segundo cuenta."
    )

    private val breakMessages = arrayOf(
        "¡Bien hecho! Relájate, mereces este descanso.",
        "Tómate un respiro, disfruta este momento de calma.",
        "¡Recarga tus energías! Un descanso productivo es clave.",
        "Respira profundamente y relájate. ¡Tu mente lo necesita!"
    )

    // Función para iniciar la sesión de concentración
    fun startFocusSession() {
        countDownTimer?.cancel() // Cancela cualquier temporizador en ejecución
        _currentPhase.value = Phase.FOCUS
        timeRemainingInMillis = 25 * 60 * 1000L // Restablece el tiempo de enfoque a 25 minutos
        _timeLeft.value = "25:00"
        _isSkipBreakButtonVisible.value = false // Ocultar el botón si estaba visible
        showNotification("Inicio de Concentración", getRandomMessage(focusMessages))
        startTimer() // Inicia el temporizador con el tiempo de enfoque actualizado
    }

    // Función para iniciar la sesión de descanso
    private fun startBreakSession() {
        _currentPhase.value = Phase.BREAK
        timeRemainingInMillis = 5 * 60 * 1000L // 5 minutos para descanso
        _timeLeft.value = "05:00"
        _isSkipBreakButtonVisible.value = true // Mostrar el botón durante el descanso
        showNotification("Inicio de Descanso", getRandomMessage(breakMessages))
        startTimer()
    }

    // Inicia o reanuda el temporizador
    fun startTimer() {
        countDownTimer?.cancel() // Cancela cualquier temporizador en ejecución antes de iniciar uno nuevo
        _isRunning.value = true

        countDownTimer = object : CountDownTimer(timeRemainingInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingInMillis = millisUntilFinished
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                _timeLeft.value = String.format("%02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                _isRunning.value = false
                when (_currentPhase.value ?: Phase.FOCUS) { // Si es null, se asume FOCUS
                    Phase.FOCUS -> startBreakSession()
                    Phase.BREAK -> startFocusSession()
                }
            }
        }.start()
    }

    // Pausa el temporizador
    fun pauseTimer() {
        countDownTimer?.cancel()
        _isRunning.value = false
    }

    // Restablece el temporizador
    fun resetTimer() {
        countDownTimer?.cancel()
        _isRunning.value = false
        _currentPhase.value = Phase.FOCUS
        timeRemainingInMillis = 25 * 60 * 1000L // Restablece a 25 minutos
        _timeLeft.value = "25:00"
        _isSkipBreakButtonVisible.value = false // Ocultar el botón al restablecer
    }

    // Muestra la notificación personalizada
    private fun showNotification(title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT // Reabrir la actividad si ya está en el stack
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notificationIcon = when (_currentPhase.value) {
            Phase.FOCUS -> R.drawable.ic_focus // Ícono para fase de concentración
            Phase.BREAK -> R.drawable.ic_break // Ícono para fase de descanso
            else -> R.drawable.ic_default // Ícono por defecto en caso de no estar en fase de concentración ni descanso
        }

        // Color para la notificación según la fase (rojo para concentración, verde para descanso)
        val notificationColor = if (_currentPhase.value == Phase.FOCUS) 0xFFFF0000.toInt() else 0xFF00FF00.toInt()

        val soundUri = if (_currentPhase.value == Phase.FOCUS)
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)  // Sonido predeterminado
        else
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)


        val builder = NotificationCompat.Builder(context, MainActivity.CHANNEL_ID)
            .setSmallIcon(notificationIcon) // Ícono personalizado
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)  // Usar el PendingIntent configurado
            .setAutoCancel(true)
            .setColor(notificationColor) // Color de la notificación
            .setSound(soundUri) // Sonido para la notificación

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            notify(MainActivity.NOTIFICATION_ID, builder.build())
        }
    }

    // Función para obtener un mensaje aleatorio
    private fun getRandomMessage(messages: Array<String>): String {
        return messages[Random.nextInt(messages.size)]
    }
}
