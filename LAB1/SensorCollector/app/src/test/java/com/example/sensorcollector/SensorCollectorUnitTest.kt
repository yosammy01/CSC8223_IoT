package com.example.sensorcollector

import com.example.sensorcollector.model.SensorReading
import com.example.sensorcollector.util.CsvExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SensorCollectorUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSensorReadingCsvLineFormatting() {
        val reading = SensorReading(
            timestampMs = 1710000000000L,
            relTimestampMs = 200L,
            x = 0.1234f,
            y = 1.5678f,
            z = 9.8100f,
            label = "Standing Still"
        )

        val csvLine = reading.toCsvLine()
        assertEquals("1710000000000,200,0.1234,1.5678,9.8100,Standing Still\n", csvLine)
    }

    @Test
    fun testCsvParsing() {
        val csvFile = tempFolder.newFile("accel_standing_still_test.csv")
        csvFile.writeText(
            SensorReading.CSV_HEADER +
                    "1710000000000,0,0.1000,0.2000,9.8100,Standing Still\n" +
                    "1710000000020,20,0.1100,0.2100,9.8000,Standing Still\n" +
                    "1710000000040,40,0.1200,0.2200,9.7900,Standing Still\n"
        )

        val session = CsvExporter.parseRecordingSession(csvFile)

        assertEquals("Standing Still", session.activityLabel)
        assertEquals(3, session.sampleCount)
        assertEquals(40L, session.durationMs)
        assertEquals(1710000000000L, session.timestampMs)
        assertTrue(session.fileSizeBytes > 0)
    }

    @Test
    fun testWalkingCsvParsing() {
        val csvFile = tempFolder.newFile("accel_walking_test.csv")
        csvFile.writeText(
            SensorReading.CSV_HEADER +
                    "1710000010000,0,1.2000,2.3000,10.5000,Walking\n" +
                    "1710000010050,50,2.1000,1.8000,8.9000,Walking\n"
        )

        val session = CsvExporter.parseRecordingSession(csvFile)

        assertEquals("Walking", session.activityLabel)
        assertEquals(2, session.sampleCount)
        assertEquals(50L, session.durationMs)
    }
}
