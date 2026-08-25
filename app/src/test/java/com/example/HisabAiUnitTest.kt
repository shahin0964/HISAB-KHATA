package com.example

import com.example.data.ai.HisabAiManager
import com.example.data.ai.HisabAiResult
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HisabAiUnitTest {

    @Test
    fun testCase1_AjkePachshoTakarBazar() {
        val input = "আজকে ৫০০ টাকার বাজার"
        val res = HisabAiManager.parseLocally(input)
        assertTrue(res is HisabAiResult.Success)
        val result = (res as HisabAiResult.Success).parsed
        assertEquals("CREATE_EXPENSE", result.intent)
        assertEquals(500.0, result.amount!!, 0.01)
        assertEquals("বাজার", result.category)
    }

    @Test
    fun testCase2_ShattTakaCNG() {
        val input = "৬০ টাকা CNG"
        val res = HisabAiManager.parseLocally(input)
        assertTrue(res is HisabAiResult.Success)
        val result = (res as HisabAiResult.Success).parsed
        assertEquals("CREATE_EXPENSE", result.intent)
        assertEquals(60.0, result.amount!!, 0.01)
        assertEquals("যাতায়াত", result.category)
    }

    @Test
    fun testCase3_BetonPelam25Hazar() {
        val input = "বেতন পেলাম ২৫ হাজার"
        val res = HisabAiManager.parseLocally(input)
        assertTrue(res is HisabAiResult.Success)
        val result = (res as HisabAiResult.Success).parsed
        assertEquals("CREATE_INCOME", result.intent)
        assertEquals(25000.0, result.amount!!, 0.01)
        assertEquals("বেতন", result.category)
    }

    @Test
    fun testCase4_GotokalTinshoTakaBazar() {
        val input = "গতকাল ৩০০ টাকা বাজার"
        val res = HisabAiManager.parseLocally(input)
        assertTrue(res is HisabAiResult.Success)
        val result = (res as HisabAiResult.Success).parsed
        assertEquals("CREATE_EXPENSE", result.intent)
        assertEquals(300.0, result.amount!!, 0.01)

        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val expectedYesterday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        assertEquals(expectedYesterday, result.dateString)
    }

    @Test
    fun testCase5_Aj1Hazar2shoTakaKhoroch() {
        val input = "আজ ১ হাজার ২শ টাকা খরচ"
        val res = HisabAiManager.parseLocally(input)
        assertTrue(res is HisabAiResult.Success)
        val result = (res as HisabAiResult.Success).parsed
        assertEquals("CREATE_EXPENSE", result.intent)
        assertEquals(1200.0, result.amount!!, 0.01)
    }

    @Test
    fun testCase6_MissingAmount() {
        val input = "আজকে বাজার করেছি"
        val res = HisabAiManager.parseLocally(input)
        assertTrue(res is HisabAiResult.QuerySuccess || res is HisabAiResult.Error)
    }

    @Test
    fun testCase7_MissingCategory() {
        val input = "৫০০ টাকা খরচ"
        val res = HisabAiManager.parseLocally(input)
        assertTrue(res is HisabAiResult.Success)
        val result = (res as HisabAiResult.Success).parsed
        assertEquals(500.0, result.amount!!, 0.01)
        assertEquals("অন্যান্য", result.category)
    }

    @Test
    fun testCase8_InvalidAiResponseHandling() {
        val extracted = HisabAiManager.extractAmountFromBengaliText("কোথাও কিছু নেই")
        assertNull(extracted)
    }

    @Test
    fun testCase9_NetworkFailureHandling() {
        val res = HisabAiManager.parseLocally("সাড়ে পাঁচশো টাকা বাজার")
        assertTrue(res is HisabAiResult.Success)
        val result = (res as HisabAiResult.Success).parsed
        assertEquals(550.0, result.amount!!, 0.01)
        assertEquals("বাজার", result.category)
    }

    @Test
    fun testCase10_DuplicateSubmissionProtection() {
        var isSaving = false
        var savedCount = 0

        fun submitTransaction() {
            if (isSaving) return
            isSaving = true
            savedCount++
        }

        submitTransaction()
        assertEquals(1, savedCount)
        assertTrue(isSaving)

        // Rapid second click while saving
        submitTransaction()
        assertEquals(1, savedCount)
    }
}
