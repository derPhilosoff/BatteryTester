package com.example.batterytester

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.nio.ShortBuffer
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    // Flag, um die Aufnahme zu steuern
    @Volatile
    private var isRecording = false
    private var isHelpClicked = false

    // AudioRecord und UI-Komponenten
    private lateinit var volumeTextView: TextView
    private lateinit var volumeResult: TextView
    private lateinit var volumeProgressBar: ProgressBar
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var resultValueTextView: TextView
    private lateinit var resultTextView: TextView
    private lateinit var helpButton: Button


    // Liste zum Speichern der Audio-Werte
    private val audioDataList = mutableListOf<Short>()

    //Map zum Hinterlegen der Dämpfungsfaktoren
    private val faktorLadung = mapOf(
        0.0..0.6 to "100%",
        0.6..0.625 to "90%",
        0.625..0.65 to "80%",
        0.65..0.675 to "70%",
        0.675..0.7 to "60%",
        0.7..0.725 to "50%",
        0.725..0.75 to "40%",
        0.75..0.775 to "30%",
        0.775..0.8 to "20%",
        0.8..10.0 to "<10%"
    )

    // Zeitpunkte der Peaks
    private val peakTimestamps = mutableListOf<Long>()

    // Schwellenwert, um ein Klatschen zu erkennen (Amplitude)
    private var noiseThreshold = 50

    // Zeitfenster für Stille nach einem Klatschen, um ein weiteres Klatschen zu erkennen (in Millisekunden)
    private var minSilenceDuration = 100

    // Letzter Zeitpunkt, an dem ein Klatschen erkannt wurde
    private var lastPeakTimestamp: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI-Elemente verbinden
        volumeTextView = findViewById(R.id.volumeTextView)
        volumeResult = findViewById(R.id.volumeResultTextView)
        volumeProgressBar = findViewById(R.id.volumeProgressBar)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        helpButton = findViewById(R.id.helpButton)
        resultTextView = findViewById(R.id.resultTextView)
        resultValueTextView = findViewById(R.id.resultValueTextView)
        val sensitivityBar = findViewById<SeekBar>(R.id.sensitivityBar)
        val editSensitivity = findViewById<EditText>(R.id.editSensitivity)
        sensitivityBar.progress = noiseThreshold
        val timeOutBar = findViewById<SeekBar>(R.id.timeOutBar)
        val editTimeOut = findViewById<EditText>(R.id.editTimeOut)
        timeOutBar.progress = minSilenceDuration

        // Button zum Starten der Aufnahme
        startButton.visibility = View.VISIBLE
        startButton.setOnClickListener {
            startButton.visibility = View.GONE
            stopButton.visibility = View.VISIBLE
            sensitivityBar.isEnabled = false
            editSensitivity.isEnabled = false
            timeOutBar.isEnabled = false
            editTimeOut.isEnabled = false
            helpButton.isEnabled = false
            startAudioRecording()
        }

        // Button zum Stoppen der Aufnahme
        stopButton.visibility = View.GONE
        stopButton.setOnClickListener {
            stopButton.visibility = View.GONE
            startButton.visibility = View.VISIBLE
            stopAudioRecording()
            sensitivityBar.isEnabled = true
            editSensitivity.isEnabled = true
            timeOutBar.isEnabled = true
            editTimeOut.isEnabled = true
            helpButton.isEnabled = true
            peakTimestamps.clear()
        }



        // Initialen Wert der EditText mit dem Wert der SeekBar synchronisieren
        editSensitivity.setText(noiseThreshold.toString())

        // SeekBar Listener - Ändere den EditText, wenn der Schieberegler bewegt wird
        sensitivityBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                editSensitivity.setText(progress.toString())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Nicht benötigt
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Nicht benötigt
            }
        })

        // EditText Listener - Ändere den Wert der SeekBar, wenn der Benutzer einen Wert eingibt
        editSensitivity.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Nicht benötigt
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Nicht benötigt
            }

            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                if (input.isNotEmpty()) {
                    val value = input.toIntOrNull() ?: 50
                    // Prüfen, ob der Wert innerhalb der Grenzen liegt
                    if (value in 0..sensitivityBar.max) {
                        noiseThreshold = value
                        sensitivityBar.progress = value
                    }
                }
            }
        })


        // Initialen Wert der EditText mit dem Wert der SeekBar synchronisieren
        editTimeOut.setText(minSilenceDuration.toString())

        // SeekBar Listener - Ändere den EditText, wenn der Schieberegler bewegt wird
        timeOutBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                editTimeOut.setText(progress.toString())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Nicht benötigt
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Nicht benötigt
            }
        })

        // EditText Listener - Ändere den Wert der SeekBar, wenn der Benutzer einen Wert eingibt
        editTimeOut.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Nicht benötigt
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Nicht benötigt
            }

            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                if (input.isNotEmpty()) {
                    val value = input.toIntOrNull() ?: 40
                    // Prüfen, ob der Wert innerhalb der Grenzen liegt
                    if (value in 0..timeOutBar.max) {
                        minSilenceDuration = value
                        timeOutBar.progress = value
                    }
                }
            }
        })

        //TODO Hilfetext schreiben

        // Button für Hilfe
        helpButton.setOnClickListener {
            if (isHelpClicked){
                isHelpClicked = false
                resultTextView.visibility = View.VISIBLE
                resultValueTextView.visibility = View.VISIBLE
                startButton.visibility = View.VISIBLE
                volumeTextView.visibility = View.VISIBLE
                volumeProgressBar.visibility = View.VISIBLE
                volumeResult.visibility = View.VISIBLE
                sensitivityBar.visibility = View.VISIBLE
                editSensitivity.visibility = View.VISIBLE
                timeOutBar.visibility = View.VISIBLE
                editTimeOut.visibility = View.VISIBLE
            }
            else{
                isHelpClicked = true
                resultTextView.visibility = View.GONE
                resultValueTextView.visibility = View.GONE
                startButton.visibility = View.GONE
                stopButton.visibility = View.GONE
                volumeTextView.visibility = View.GONE
                volumeProgressBar.visibility = View.GONE
                volumeResult.visibility = View.GONE
                sensitivityBar.visibility = View.GONE
                editSensitivity.visibility = View.GONE
                timeOutBar.visibility = View.GONE
                editTimeOut.visibility = View.GONE
                stopAudioRecording()
            }
        }



    }

    private fun startAudioRecording() {
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)


        //TODO vollständiges Permissionhandling!
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            0
        )

        val audioRecord = if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        } else {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
        }

        val audioBuffer = ShortArray(bufferSize)

        isRecording = true

        // Starten der Audio-Aufnahme
        audioRecord.startRecording()

        // Hintergrund-Thread zum Lesen der Audiodaten
        Thread {
            while (isRecording) {
                val readSize = audioRecord.read(audioBuffer, 0, audioBuffer.size)

                if (readSize > 0) {
                    val shortBuffer = ShortBuffer.wrap(audioBuffer)
                    val volumeLevelDb = calculateVolumeInDB(audioBuffer)

                    //Alternative zur Berechnung in Decibel; Genauere Messergebnisse
                    // val volumeLevel = calculateVolume(shortBuffer)

                    // Speichere die Werte aus dem Buffer in der Liste
                    for (i in 0 until shortBuffer.limit()) {
                        audioDataList.add(shortBuffer[i])
                    }

                    // Erkenne Klatschen anhand der Lautstärke
                    detectPeak(volumeLevelDb.roundToInt())

                    // Aktualisiere die Lautstärke im UI-Thread
                    runOnUiThread {
                        volumeResult.text = "${volumeLevelDb.roundToInt()}"
                        volumeProgressBar.progress = volumeLevelDb.roundToInt()
                    }
                }
            }
            // Beenden der Aufnahme und Freigeben der Ressourcen
            audioRecord.stop()
            audioRecord.release()

        }.start()

    }

    private fun stopAudioRecording() {
        isRecording = false
        // Ausgabe der gespeicherten Daten (nur zum Debuggen)
        println("Gespeicherte Audio-Daten: ${audioDataList.size} Samples")
    }



    /*
    Methode zur Berechnung der Lautstärke aus dem ShortBuffer
    Falls Berechnung mit dB zu ungenau kann diese Funktion genutzt werden

    private fun calculateVolume(buffer: ShortBuffer): Int {
        var sum = 0.0
        for (i in 0 until buffer.limit()) {
            sum += abs(buffer[i].toDouble())
        }
        // Normalisiere den Wert für die ProgressBar (zwischen 0 und 32767)
        return (sum / buffer.limit()).toInt()
    }
     */

    //Funktion zur Berechnung der Lautstärke in dB
    private fun calculateVolumeInDB(buffer: ShortArray):Double{
        var sumOfSquares = 0.0
        val sampleCount = buffer.size

        // Schritt 1: Berechne die Summe der Quadrate der Short-Werte
        for (sample in buffer) {
            sumOfSquares += sample * sample
        }

        // Schritt 2: Berechne den RMS-Wert
        val rms = sqrt(sumOfSquares / sampleCount)

        // Schritt 3: Berechne den Schallpegel in Dezibel
        // Referenzwert ist 32767 (der maximale Wert eines 16-Bit-Samples)
        // Theoretisch sollte in der dB Berechnung rms durch den Referenzwert geteilt werden
        // Scheinbar funktioniert aber rms trotzdem, ohne etwas zu ändern
        // Gemessene Werte verifiziert durch externe App "Sound Meter"
        // val reference = 32767.0
        val db = 20 * log10(rms)

        return db
    }

    // Methode zur Erkennung eines Ereignisses
    private fun detectPeak(volumeLevelDb: Int) {
        // Erkenne ein Ereignis, wenn die Lautstärke den Schwellenwert überschreitet
        if (volumeLevelDb > noiseThreshold) {
            val currentTimestamp = SystemClock.elapsedRealtime()

            // Stelle sicher, dass eine gewisse Stille seit dem letzten Ereignis vergangen ist
            if (currentTimestamp - lastPeakTimestamp > minSilenceDuration) {
                lastPeakTimestamp = currentTimestamp

                // Speichere den Zeitpunkt des Ereignisses
                peakTimestamps.add(currentTimestamp)

                // Wenn drei Ereignisse erkannt wurden, berechne die Zeitdifferenzen
                if (peakTimestamps.size == 3) {
                    calculatePeakIntervals()
                    // Leere die Timestamps-Liste nach drei Ereignissen
                    peakTimestamps.clear()
                }
            }
        }
    }

    // Methode zur Berechnung der Zeitintervalle zwischen den Klatschern
    private fun calculatePeakIntervals() {
        if (peakTimestamps.size == 3) {
            val interval1 : Long = peakTimestamps[1] - peakTimestamps[0]
            val interval2 : Long = peakTimestamps[2] - peakTimestamps[1]

            val factor : Double = interval2.toDouble() / interval1.toDouble()

            val batteryStatus = getBatteryStatus(factor)


            // Zeige die Intervalle auf dem Bildschirm an
            runOnUiThread {
                resultValueTextView.visibility =View.VISIBLE
                if (batteryStatus == getString(R.string.unknownResult)){
                    resultTextView.text = getString(R.string.resultTextViewErr)
                    resultValueTextView.text = " "
                }
                else{
                    resultTextView.text = getString(R.string.resultTextViewSecondary)
                    resultValueTextView.text = batteryStatus
                }

                //TODO TESTING
                stopButton.performClick()
            }
        }
    }

    private fun getBatteryStatus(value: Double): String {
        for ((range, text) in faktorLadung) {
            if (value in range) {
                return text
            }
        }
        // Standardwert, falls kein passender Bereich gefunden wurde
        return getString(R.string.unknownResult)
    }
}


//TODO APK auf Handys allgemein
//TODO Playstore???
//TODO dB in dB A oder was genau?
//TODO Eigene Messungen, OBERFLÄCHENMATERIAL!
//TODO Abfrage welche Batterien es sind (AA, AAA, 9V Blöcke)
//TODO App Icon Drawable?
//TODO App Theme Colors --> Mehr davon
//TODO Schönes UI
//TODO Zeitmessung verifizieren --> Zeitabstände mit Soundgenerator in ms Bereichen
//TODO Batterie verhält sich anders wenn frisch entladen, bei anderen Temperaturen <-- Help Text!