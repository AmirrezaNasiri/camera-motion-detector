package com.example.cameramotiondetector

import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

import androidx.core.content.ContextCompat.getSystemService




class AlertManager(private var activity: AppCompatActivity) {
    private var imgActivityIndicator: ImageView = activity.findViewById(R.id.imgActivityIndicator)
    private var imgVolumeIndicator: ImageView = activity.findViewById(R.id.imgVolumeIndicator)
    private var imgCameraIndicator: ImageView = activity.findViewById(R.id.imgCameraIndicator)
    private var imgMotionIndicator: ImageView = activity.findViewById(R.id.imgMotionIndicator)
    private var toast: Toast? = null

    fun toast(message: String) {
        toast?.cancel()

        toast = Toast.makeText(activity, message, Toast.LENGTH_LONG)
        toast?.show()
    }

    fun init() {
        initVolumeChecker()
    }

    private fun initVolumeChecker() {
        val mainHandler = Handler(Looper.getMainLooper())

        mainHandler.post(object : Runnable {
            override fun run() {
                val audioManager = getSystemService(activity, AudioManager::class.java)
                val currentVolume = audioManager!!.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val currentVolumePercentage = 100 * currentVolume / maxVolume

                if (currentVolumePercentage < 50) {
                    badIndicator(imgVolumeIndicator)
                    playWarning()
                } else {
                    goodIndicator(imgVolumeIndicator)
                }

                mainHandler.postDelayed(this, 1000)
            }
        })
    }

    fun activityStarted() {
        goodIndicator(imgActivityIndicator)
    }

    fun activityStopped() {
        badIndicator(imgActivityIndicator)
        playWarning()
    }

    fun cameraWorking() {
        goodIndicator(imgCameraIndicator)
    }

    fun cameraFailed() {
        badIndicator(imgCameraIndicator)
        playError()
    }

    fun motionNotDetected() {
        goodIndicator(imgMotionIndicator)
    }

    fun motionDetected() {
        badIndicator(imgMotionIndicator)
        playError()
    }

    private fun badIndicator(indicator: ImageView) {
        indicator.setImageDrawable(AppCompatResources.getDrawable(activity, R.drawable.red_icon))
    }

    private fun goodIndicator(indicator: ImageView) {
        indicator.setImageDrawable(AppCompatResources.getDrawable(activity, R.drawable.green_icon))
    }

    private fun playWarning() {
        ToneGenerator(AudioManager.STREAM_MUSIC, 100).startTone(ToneGenerator.TONE_PROP_BEEP, 200)
    }

    private fun playError() {
        ToneGenerator(AudioManager.STREAM_MUSIC, 100).startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
    }
}