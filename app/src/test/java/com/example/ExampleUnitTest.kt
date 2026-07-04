package com.example

import com.example.utils.ScripExtractor
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testScripMatching_Equities() {
    assertTrue(ScripExtractor.matchesAnyScrip("RELIANCE"))
    assertTrue(ScripExtractor.matchesAnyScrip("TCS"))
    assertTrue(ScripExtractor.matchesAnyScrip("ZOMATO"))
    assertTrue(ScripExtractor.matchesAnyScrip("PAYTM"))
    assertFalse(ScripExtractor.matchesAnyScrip("INVALIDTICKERNAME"))
  }

  @Test
  fun testScripMatching_Commodities() {
    assertTrue(ScripExtractor.matchesAnyScrip("GOLD"))
    assertTrue(ScripExtractor.matchesAnyScrip("SILVER"))
    assertTrue(ScripExtractor.matchesAnyScrip("CRUDEOIL"))
  }

  @Test
  fun testScripMatching_Derivatives() {
    assertTrue(ScripExtractor.matchesAnyScrip("NIFTY"))
    assertTrue(ScripExtractor.matchesAnyScrip("BANKNIFTY"))
    assertTrue(ScripExtractor.matchesAnyScrip("NIFTY24DEC25000CE"))
    assertTrue(ScripExtractor.matchesAnyScrip("GOLD24OCTFUT"))
    assertTrue(ScripExtractor.matchesAnyScrip("TCS24PE"))
  }

  @Test
  fun testScripCategorization() {
    assertEquals("Equity", ScripExtractor.getScripCategory("RELIANCE"))
    assertEquals("Equity", ScripExtractor.getScripCategory("ZOMATO"))
    assertEquals("Commodity", ScripExtractor.getScripCategory("GOLD"))
    assertEquals("Commodity", ScripExtractor.getScripCategory("CRUDEOIL"))
    assertEquals("Derivative", ScripExtractor.getScripCategory("NIFTY"))
    assertEquals("Derivative", ScripExtractor.getScripCategory("NIFTY24DEC25000CE"))
    assertEquals("Derivative", ScripExtractor.getScripCategory("GOLD24OCTFUT"))
  }

  @Test
  fun testFuzzyAndAliasExtraction() {
    val articleText = """
      Hindustan Aeronautics (HAL) and Bharat Electronics (BEL) rose ahead of DAC meeting.
      Other gainers included Bharat Dynamics (BDL), Mazagon Dock Shipbuilders (MAZDOCK), 
      and Paras Defence and Space Technologies (PARAS).
      Meanwhile, Reliance rose and Airtel shares fell.
      Some general text mentioning gold standard and LTCG tax increases.
    """.trimIndent()

    val extracted = ScripExtractor.extractScripsFromText(articleText)

    // Verify all specified stocks are extracted correctly
    assertTrue(extracted.contains("BEL"))
    assertTrue(extracted.contains("HAL"))
    assertTrue(extracted.contains("BDL"))
    assertTrue(extracted.contains("MAZDOCK"))
    assertTrue(extracted.contains("PARAS"))
    assertTrue(extracted.contains("RELIANCE"))
    assertTrue(extracted.contains("BHARTIARTL"))

    // Verify false positives are filtered out
    assertFalse(extracted.contains("LT")) // LTCG should not extract L&T (LT)
    assertFalse(extracted.contains("GOLD")) // lowercase "gold standard" should not extract GOLD commodity
  }
}
