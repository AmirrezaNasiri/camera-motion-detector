package com.example.cameramotiondetector

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import java.net.URL
import java.sql.Timestamp
import java.util.*

class MainActivity : AppCompatActivity() {
    var boxes = arrayOf<Array<Int>>()
    var movements = arrayOf<Array<Int>>()

    var isStarted = false
    var targetFrameMat: Mat? = null
    lateinit var lastFrame: Mat
    lateinit var spotMask: Mat
    private lateinit var alertManager: AlertManager

    // Components
    private lateinit var imgRaw: ImageView
    private lateinit var imgLast: ImageView
    private lateinit var imgTarget: ImageView
    private lateinit var imgDiff: ImageView

    private var lastFrameTimestamp: Long? = null

    // Temp
    var frameIndex = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //val policy = ThreadPolicy.Builder().permitAll().build()
        //StrictMode.setThreadPolicy(policy)

        OpenCVLoader.initDebug()
        setContentView(R.layout.activity_main)
        setUpComponents()
        setUpStreamer()

        alertManager = AlertManager(this)
        alertManager.init()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setUpComponents() {
        imgRaw = findViewById(R.id.imgRaw)
        imgLast = findViewById(R.id.imgProcessedTargetFrame)
        imgTarget = findViewById(R.id.imgTargetFrame)
        imgDiff = findViewById(R.id.imgDiff)

        findViewById<Button>(R.id.btnRetarget).setOnClickListener {
            if (!isStarted) {
                alertManager.toast("Not started yet")
            } else {
                saveTargetFrame()
                alertManager.toast("✔ Captured")
            }
        }

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            if (!isStarted) {
                alertManager.toast("Already stopped")
            } else {
                isStarted = false
                boxes = arrayOf()
                refreshRawImage()
                alertManager.toast("✔ Stopped")
                alertManager.activityStopped()
            }
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (isStarted) {
                alertManager.toast("Already started")
            } else {
                refreshMotionDetector()
                isStarted = true
                alertManager.toast("✔ Started")
                alertManager.activityStarted()
            }
        }

        var lastX = 0
        var lastY = 0

        imgRaw.setOnTouchListener { _, event ->
            var x = event.x.toInt()
            var y = event.y.toInt()

            x -= imgRaw.left
            y -= imgRaw.top

            val dimX = x * lastFrame.cols() / (imgRaw.right - imgRaw.left)
            val dimY = y * lastFrame.rows() / (imgRaw.bottom - imgRaw.top)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = dimX
                    lastY = dimY
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isStarted) {
                        boxes += arrayOf(
                            lastX, lastY,
                            dimX,
                            dimY
                        )

                        refreshRawImage()
                    }
                }
            }

            true
        }
    }

    fun setUpStreamer() {
        val mainHandler = Handler(Looper.getMainLooper())

        mainHandler.post(object : Runnable {
            override fun run() {

                val thread = Thread {
                    try {

                        val url = URL(
                            findViewById<EditText>(R.id.txtCaptureUri).text.toString() ?: "http://192.168.1.6/camera/jpg.php"
                        )

                        val connection = url.openConnection()
                        connection.connectTimeout = 400

                        lastFrame = bitmap2mat(
                            BitmapFactory.decodeStream(connection.getInputStream())
                        )

                        val now = System.currentTimeMillis()

                        runOnUiThread {
                            alertManager.cameraWorking()
                            refreshRawImage()

                            if (isStarted) {
                                doMotionDetect()
                            }


                            if (lastFrameTimestamp != null) {
                                Log.i("[aa]", "${lastFrameTimestamp!!}:${now} ${now - lastFrameTimestamp!!}")
                            }

                            if (lastFrameTimestamp != null && now - lastFrameTimestamp!! > 800) {
                                alertManager.cameraFailed()
                            }

                            lastFrameTimestamp = now
                        }

                    } catch (e: Exception) {
                        runOnUiThread {
                            if (isStarted) {
                                alertManager.cameraFailed()
                            }
                            alertManager.toast("Camera Error: ${e.message}")
                        }
                    } finally {
                        var interval = findViewById<EditText>(R.id.txtCaptureInterval).text.toString().toInt()
                        if (interval < 50) {
                            interval = 50
                        } else if (interval > 2000) {
                            interval = 2000
                        }

                        mainHandler.postDelayed(this, interval.toLong())
                    }
                }

                thread.start()


            }
        })
    }

    fun refreshMotionDetector() {
        prepareSpotMask()
        saveTargetFrame()
        doMotionDetect()
    }

    fun doMotionDetect() {
        val last = processFrame(lastFrame)
        val tmp = Mat()

        val minArea = findViewById<EditText>(R.id.txtMinArea).text.toString().toInt()
        val thrMinAreas = findViewById<EditText>(R.id.txtThrMinAreas).text.toString().toInt()
        val thrMinSingleArea = findViewById<EditText>(R.id.txtThrMinSingleArea).text.toString().toInt()

        imgLast.setImageBitmap(mat2bitmap(last))

        Core.absdiff(last, targetFrameMat, tmp)

        // TODO: Configurable
        Imgproc.threshold(tmp, tmp, 25.0, 255.0, Imgproc.THRESH_BINARY)
        Imgproc.dilate(tmp, tmp, Mat())

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(tmp, contours, Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE)

        var biggestArea = 0.0
        movements = arrayOf()
        for (it in contours) {
            val area = Imgproc.contourArea(it)

            if (area > biggestArea) {
                biggestArea = area
            }

            if (area < minArea) {
                continue
            }

            val rect = Imgproc.boundingRect(it)
            movements += arrayOf(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height)
        }

        // TODO: Configurables
        if (movements.count() >= thrMinAreas || biggestArea >= thrMinSingleArea ) {
            alertManager.motionDetected()
        } else {
            alertManager.motionNotDetected()
        }

        imgDiff.setImageBitmap(mat2bitmap(tmp))

        refreshRawImage()
    }

    fun refreshRawImage() {
        val mat = Mat()
        lastFrame.copyTo(mat)

        if (!isStarted) {
            boxes.forEach {
                Imgproc.rectangle(
                    mat,
                    Point(it[0].toDouble(), it[1].toDouble()),
                    Point(it[2].toDouble(), it[3].toDouble()),
                    Scalar(255.0, 0.0, 0.0, 0.5),
                    5
                    )
            }
        }

        if (isStarted) {
            movements.forEach {
                Imgproc.rectangle(
                    mat,
                    Point(it[0].toDouble(), it[1].toDouble()),
                    Point(it[2].toDouble(), it[3].toDouble()),
                    Scalar(0.0, 255.0, 0.0, 0.5),
                    5
                )
            }
        }

        imgRaw.setImageBitmap(mat2bitmap(mat))
        imgRaw.invalidate()
    }

    fun saveTargetFrame() {
        targetFrameMat = processFrame(lastFrame)
        imgTarget.setImageBitmap(mat2bitmap(targetFrameMat!!))
    }

    fun prepareSpotMask() {
        spotMask = Mat(lastFrame.rows(), lastFrame.cols(), CvType.CV_8U, Scalar.all(0.0))

        boxes.forEach {
            Imgproc.rectangle(
                spotMask,
                Point(it[0].toDouble(), it[1].toDouble()),
                Point(it[2].toDouble(), it[3].toDouble()),
                Scalar(255.0, 255.0, 255.0),
                -1,
                8,
                0
            )
        }
    }

    fun processFrame(frame: Mat): Mat {
        val result = Mat()

        // Apply mask
        frame.copyTo(result, spotMask)

        // Grayscale
        Imgproc.cvtColor(result, result, Imgproc.COLOR_RGB2GRAY)

        // Smoothing
        Imgproc.GaussianBlur(result, result, Size(21.0, 21.0), 0.0)

        return result
    }

    private fun mat2bitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)

        return bitmap
    }

    private fun bitmap2mat(frame: Bitmap): Mat {
        val mat = Mat()
        Utils.bitmapToMat(frame, mat)

        return mat
    }
}